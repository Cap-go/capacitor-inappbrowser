/* global Bun, URL, Response, console, process */

const port = Number(process.env.PROXY_REGRESSION_PORT || '8123');

const entryHtml = `<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <title>Proxy Regression Entry</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  </head>
  <body>
    <main>
      <h1>Proxy Regression Entry</h1>
      <p>Waiting for proxy assertions...</p>
      <script src="/app.js"></script>
    </main>
  </body>
</html>`;

const appJs = `(() => {
  const setVisibleResult = (message, details) => {
    document.title = message;
    document.body.dataset.proxyRegressionStatus = message;

    let banner = document.getElementById('proxy-regression-result');
    if (!banner) {
      banner = document.createElement('div');
      banner.id = 'proxy-regression-result';
      banner.style.position = 'fixed';
      banner.style.top = '16px';
      banner.style.left = '16px';
      banner.style.right = '16px';
      banner.style.zIndex = '2147483647';
      banner.style.padding = '14px 16px';
      banner.style.borderRadius = '12px';
      banner.style.background = 'rgba(17, 24, 39, 0.96)';
      banner.style.color = '#fff';
      banner.style.fontFamily = 'system-ui, sans-serif';
      banner.style.fontSize = '16px';
      banner.style.whiteSpace = 'pre-wrap';
      banner.style.boxShadow = '0 10px 32px rgba(0, 0, 0, 0.35)';
      document.body.appendChild(banner);
    }

    banner.textContent = details ? message + '\\n' + details : message;
    window.alert(message);
  };

  const postResult = (detail) => {
    if (window.mobileApp && typeof window.mobileApp.postMessage === 'function') {
      window.mobileApp.postMessage({ detail });
    }
  };

  const parseJson = async (response) => {
    if (!response.ok) {
      throw new Error('HTTP ' + response.status);
    }
    const payload = await response.json();
    return {
      ...payload,
      responseHeaders: Object.fromEntries(response.headers.entries()),
    };
  };

  const loadXhr = () =>
    new Promise((resolve, reject) => {
      const request = new XMLHttpRequest();
      request.open('POST', '/api/xhr', true);
      request.setRequestHeader('Content-Type', 'application/json');
      request.onload = () => {
        try {
          resolve(JSON.parse(request.responseText));
        } catch (error) {
          reject(error);
        }
      };
      request.onerror = () => reject(new Error('xhr-request-failed'));
      request.send(JSON.stringify({ source: 'xhr', changed: false }));
    });

  (async () => {
    try {
      const metaResult = await fetch('/api/meta', {
        headers: {
          'X-Client': 'meta',
        },
      }).then(parseJson);

      const fetchResult = await fetch('/api/fetch', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Client': 'fetch',
        },
        body: JSON.stringify({ source: 'fetch', changed: false }),
      }).then(parseJson);

      const xhrResult = await loadXhr();

      const fetchBody = JSON.parse(fetchResult.body || '{}');
      const xhrBody = JSON.parse(xhrResult.body || '{}');

      const summary = {
        entryResponse: document.querySelector('meta[name="proxy-entry"]')?.content === 'rewritten',
        metaRequest: metaResult.headers?.['x-proxy-meta-request'] === 'meta-request',
        metaResponse: metaResult.responseHeaders?.['x-proxy-meta-response'] === 'meta-response',
        fetchRequest: fetchResult.headers?.['x-proxy-request-rule'] === 'fetch-request',
        fetchRequestBody: fetchBody.changed === true && fetchBody.requestRule === 'fetch-request',
        fetchResponse: fetchResult.proxyResponseRule === 'fetch-response',
        xhrRequest: xhrResult.headers?.['x-proxy-request-rule'] === 'xhr-request',
        xhrRequestBody: xhrBody.changed === true && xhrBody.requestRule === 'xhr-request',
        xhrResponse: xhrResult.proxyResponseRule === 'xhr-response',
      };

      const failed = Object.entries(summary)
        .filter(([, value]) => !value)
        .map(([key]) => key);

      const message = failed.length === 0 ? 'Proxy regression passed' : 'Proxy regression failed';
      setVisibleResult(message, failed.join(', '));

      postResult({
        type: 'proxyRegression',
        state: failed.length === 0 ? 'passed' : 'failed',
        summary,
        failed,
      });
    } catch (error) {
      const reason = error && error.message ? error.message : String(error);
      setVisibleResult('Proxy regression failed', reason);
      postResult({
        type: 'proxyRegression',
        state: 'failed',
        reason,
      });
    }
  })();
})();`;

function jsonResponse(payload) {
  return new Response(JSON.stringify(payload), {
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': 'no-store',
    },
  });
}

function textResponse(body, contentType) {
  return new Response(body, {
    headers: {
      'Content-Type': contentType,
      'Cache-Control': 'no-store',
    },
  });
}

async function echoRequest(request, pathname) {
  const body = await request.text();
  return jsonResponse({
    path: pathname,
    method: request.method,
    headers: Object.fromEntries(request.headers.entries()),
    body,
  });
}

const server = Bun.serve({
  hostname: '0.0.0.0',
  port,
  async fetch(request) {
    const url = new URL(request.url);
    console.log(`[proxy-regression-server] ${request.method} ${url.pathname}`);

    if (url.pathname === '/health') {
      return new Response('ok');
    }

    if (url.pathname === '/entry') {
      return textResponse(entryHtml, 'text/html; charset=utf-8');
    }

    if (url.pathname === '/app.js') {
      return textResponse(appJs, 'application/javascript; charset=utf-8');
    }

    if (url.pathname === '/api/meta' || url.pathname === '/api/fetch' || url.pathname === '/api/xhr') {
      return echoRequest(request, url.pathname);
    }

    if (url.pathname === '/favicon.ico') {
      return new Response(null, { status: 204 });
    }

    return new Response('Not found', { status: 404 });
  },
});

console.log(`[proxy-regression-server] listening on http://0.0.0.0:${server.port}`);
