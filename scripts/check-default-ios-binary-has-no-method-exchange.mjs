#!/usr/bin/env node
/**
 * Confirms the default iOS plugin build does not reference method_exchangeImplementations.
 * Pass the xcodebuild -derivedDataPath directory from a build that did not set
 * CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE.
 */

import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const derivedDataPath = process.argv[2];

if (!derivedDataPath) {
  console.error("Usage: check-default-ios-binary-has-no-method-exchange.mjs <derivedDataPath>");
  process.exit(1);
}

function walk(dir, files = []) {
  if (!fs.existsSync(dir)) {
    return files;
  }
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath, files);
    } else if (entry.name.endsWith(".o") || entry.name.endsWith(".a")) {
      files.push(fullPath);
    }
  }
  return files;
}

function isPluginObject(filePath) {
  const normalized = filePath.split(path.sep).join("/");
  if (normalized.includes("InappbrowserPluginTests")) {
    return false;
  }
  return (
    normalized.endsWith("/InappbrowserPlugin.o") ||
    normalized.includes("/InappbrowserPlugin.build/") ||
    normalized.includes("/InappbrowserPlugin-t.build/")
  );
}

const objects = walk(derivedDataPath).filter(isPluginObject);

if (objects.length === 0) {
  console.error(`No InappbrowserPlugin object files found under ${derivedDataPath}`);
  process.exit(1);
}

const hits = [];
for (const objectFile of objects) {
  let output = "";
  try {
    output = execFileSync("nm", [objectFile], { encoding: "utf8" });
  } catch (error) {
    output = `${error.stdout ?? ""}${error.stderr ?? ""}`;
  }
  if (output.includes("method_exchangeImplementations")) {
    hits.push(objectFile);
  }
}

if (hits.length > 0) {
  console.error("Default iOS build still references method_exchangeImplementations:");
  for (const hit of hits) {
    console.error(`  ${hit}`);
  }
  process.exit(1);
}

console.log(
  `Default iOS build has no method_exchangeImplementations reference (${objects.length} plugin objects scanned).`,
);
