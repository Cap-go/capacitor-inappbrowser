#!/usr/bin/env node
// Local HTTP server for the download lifecycle Maestro flows.
// The Android emulator reaches it at 10.0.2.2, the iOS simulator at 127.0.0.1.
// Usage: node example-app/scripts/download-lifecycle-server.mjs [port]
import { createServer } from 'node:http';

const port = Number(process.argv[2] || process.env.DOWNLOAD_LIFECYCLE_PORT || 8787);

// Each scenario is a file the in-app browser downloads with handleDownloads: true.
// complete: small and steady, so several progress samples arrive before downloadCompleted.
// cancel and kill: slow enough that the flow can cancel or kill the app mid-transfer.
// fail: announces a length, sends part of it, then drops the connection (network failure).
const scenarios = {
  complete: { size: 256 * 1024, chunks: 8, delayMs: 200 },
  cancel: { size: 2 * 1024 * 1024, chunks: 64, delayMs: 250 },
  kill: { size: 2 * 1024 * 1024, chunks: 64, delayMs: 250 },
  fail: { size: 256 * 1024, chunks: 8, delayMs: 200, dropAfterChunks: 2 },
};

function fillBuffer(size) {
  const line = Buffer.from('capgo download lifecycle e2e payload\n');
  const out = Buffer.alloc(size);
  for (let offset = 0; offset < size; offset += line.length) {
    line.copy(out, offset, 0, Math.min(line.length, size - offset));
  }
  return out;
}

function pageHtml(kind) {
  return `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Download Lifecycle ${kind}</title>
  </head>
  <body style="font-family: sans-serif; padding: 24px">
    <h1>Download Lifecycle ${kind}</h1>
    <!-- The Maestro harness clicks this link with executeScript once browserPageLoaded fires. -->
    <a id="download-link" href="/file/${kind}" download="capgo-lifecycle-${kind}.txt">Download ${kind}</a>
  </body>
</html>`;
}

function log(message) {
  process.stdout.write(`[download-lifecycle-server] ${new Date().toISOString()} ${message}\n`);
}

const server = createServer((req, res) => {
  const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
  log(`${req.method} ${url.pathname}${url.search}`);

  if (url.pathname === '/health') {
    res.writeHead(200, { 'Content-Type': 'text/plain', 'Cache-Control': 'no-store' });
    res.end('ok');
    return;
  }

  const pageMatch = /^\/page\/(complete|cancel|kill|fail)$/.exec(url.pathname);
  if (pageMatch) {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
    res.end(pageHtml(pageMatch[1]));
    return;
  }

  const fileMatch = /^\/file\/(complete|cancel|kill|fail)$/.exec(url.pathname);
  if (!fileMatch) {
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('not found');
    return;
  }

  const kind = fileMatch[1];
  const scenario = scenarios[kind];
  const body = fillBuffer(scenario.size);
  const chunkSize = Math.ceil(scenario.size / scenario.chunks);
  res.writeHead(200, {
    'Content-Type': 'text/plain; charset=utf-8',
    'Content-Length': String(scenario.size),
    'Content-Disposition': `attachment; filename="capgo-lifecycle-${kind}.txt"`,
    'Cache-Control': 'no-store',
  });

  let index = 0;
  let closed = false;
  req.on('close', () => {
    closed = true;
  });

  const writeNext = () => {
    if (closed) {
      log(`${kind}: client closed after ${index} chunks`);
      return;
    }
    if (scenario.dropAfterChunks !== undefined && index >= scenario.dropAfterChunks) {
      log(`${kind}: dropping connection after ${index} chunks`);
      res.socket?.destroy();
      return;
    }
    if (index >= scenario.chunks) {
      res.end();
      log(`${kind}: finished`);
      return;
    }
    res.write(body.subarray(index * chunkSize, Math.min(scenario.size, (index + 1) * chunkSize)));
    index += 1;
    setTimeout(writeNext, scenario.delayMs);
  };
  writeNext();
});

server.listen(port, '127.0.0.1', () => {
  log(`listening on ${port}`);
});

// Optional hard stop so a local run never leaves the server behind.
const exitAfterMs = Number(process.env.DOWNLOAD_LIFECYCLE_EXIT_AFTER_MS || 0);
if (exitAfterMs > 0) {
  setTimeout(() => {
    log(`exiting after ${exitAfterMs} ms`);
    process.exit(0);
  }, exitAfterMs).unref();
}
