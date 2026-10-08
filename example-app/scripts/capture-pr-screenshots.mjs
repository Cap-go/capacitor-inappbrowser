import { createServer } from "node:http";
import { mkdir, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const distDir = path.resolve(__dirname, "../dist");
const outDir = path.resolve(__dirname, "../screenshots");

const mime = {
  ".html": "text/html",
  ".js": "text/javascript",
  ".css": "text/css",
  ".json": "application/json",
  ".ico": "image/x-icon",
  ".webp": "image/webp",
  ".png": "image/png",
  ".svg": "image/svg+xml",
};

function createStaticServer() {
  return createServer(async (req, res) => {
    try {
      const urlPath = decodeURIComponent(req.url?.split("?")[0] || "/");
      const filePath = path.join(distDir, urlPath === "/" ? "index.html" : urlPath);
      const data = await readFile(filePath);
      const ext = path.extname(filePath);
      res.writeHead(200, { "Content-Type": mime[ext] || "application/octet-stream" });
      res.end(data);
    } catch {
      res.writeHead(404);
      res.end("Not found");
    }
  });
}

async function main() {
  await mkdir(outDir, { recursive: true });
  const server = createStaticServer();
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const port = server.address().port;
  const baseUrl = `http://127.0.0.1:${port}/`;

  const browser = await chromium.launch();
  const mobile = await browser.newPage({ viewport: { width: 390, height: 844 } });
  await mobile.goto(baseUrl, { waitUntil: "load" });
  const mobilePng = path.join(outDir, "example-mobile.png");
  const desktopPng = path.join(outDir, "example-desktop.png");
  await mobile.screenshot({ path: mobilePng, type: "png", fullPage: true });

  const desktop = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  await desktop.goto(baseUrl, { waitUntil: "load" });
  await desktop.screenshot({ path: desktopPng, type: "png", fullPage: true });

  await browser.close();
  server.close();
  console.log("Screenshots saved to", outDir);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
