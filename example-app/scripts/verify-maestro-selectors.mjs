import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const exampleDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const rootDir = path.resolve(exampleDir, "..");
const flowDirs = [path.join(rootDir, ".maestro"), path.join(exampleDir, ".maestro")];

const sourceFiles = [
  path.join(exampleDir, "src/index.html"),
  path.join(exampleDir, "src/js/capacitor-welcome.js"),
  path.join(exampleDir, "src/js/feature-smoke.js"),
  path.join(exampleDir, "src/js/keyboard-regression.js"),
  path.join(exampleDir, "src/js/proxy-regression.js"),
];

const NATIVE_IDS = new Set(["app.capgo.inappbrowser:id/closeButton"]);

async function loadCorpus() {
  const parts = await Promise.all(sourceFiles.map((file) => readFile(file, "utf8")));
  return parts.join("\n");
}

async function listYamlFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await listYamlFiles(full)));
    } else if (entry.name.endsWith(".yaml") || entry.name.endsWith(".yml")) {
      files.push(full);
    }
  }
  return files;
}

function collectSelectors(yamlText) {
  const ids = new Set();
  const texts = new Set();

  for (const match of yamlText.matchAll(/^\s*id:\s*['"]?([^'"\n]+)['"]?\s*$/gm)) {
    ids.add(match[1].trim());
  }

  for (const match of yamlText.matchAll(/^\s*(?:visible|text):\s*['"]([^'"]+)['"]\s*$/gm)) {
    const raw = match[1].trim();
    if (raw.includes("(-") || raw.includes("\\d") || raw.includes("\\.")) {
      continue;
    }
    if (raw.includes(" | ")) {
      raw.split(" | ").forEach((part) => texts.add(part.trim()));
      continue;
    }
    texts.add(raw.replaceAll("\\\\(", "(").replaceAll("\\\\)", ")"));
  }

  return { ids, texts };
}

async function main() {
  const corpus = await loadCorpus();
  const yamlFiles = [];
  for (const dir of flowDirs) {
    yamlFiles.push(...(await listYamlFiles(dir)));
  }

  const allIds = new Set();
  const allTexts = new Set();
  for (const file of yamlFiles) {
    const yamlText = await readFile(file, "utf8");
    const { ids, texts } = collectSelectors(yamlText);
    ids.forEach((id) => allIds.add(id));
    texts.forEach((text) => allTexts.add(text));
  }

  const missingIds = [];
  for (const id of allIds) {
    if (NATIVE_IDS.has(id)) {
      continue;
    }
    if (!corpus.includes(id)) {
      missingIds.push(id);
    }
  }

  const missingTexts = [];
  for (const text of allTexts) {
    if (text === "Wait" || text === "Don't allow") {
      continue;
    }
    if (!corpus.includes(text)) {
      missingTexts.push(text);
    }
  }

  if (missingIds.length > 0 || missingTexts.length > 0) {
    console.error("Maestro selector verification failed.");
    if (missingIds.length > 0) {
      console.error("Missing ids:", missingIds.join(", "));
    }
    if (missingTexts.length > 0) {
      console.error("Missing visible/text strings:", missingTexts.join(" | "));
    }
    process.exit(1);
  }

  console.log(
    `Maestro selector verification passed (${allIds.size} ids, ${allTexts.size} text selectors across ${yamlFiles.length} flows).`,
  );
}

await main();
