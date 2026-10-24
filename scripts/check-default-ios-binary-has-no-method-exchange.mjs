#!/usr/bin/env node
/**
 * Confirms whether an iOS plugin build references method_exchangeImplementations.
 * Pass the xcodebuild -derivedDataPath directory and `present` or `absent`.
 * The default package build omits the swizzle, so verify:ios passes `absent`.
 * A build made after httpProxySwizzle: true must pass `present`.
 */

import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const derivedDataPath = process.argv[2];
const expectation = process.argv[3];

if (!derivedDataPath || (expectation !== "present" && expectation !== "absent")) {
  console.error(
    "Usage: check-default-ios-binary-has-no-method-exchange.mjs <derivedDataPath> <present|absent>",
  );
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

// The linked InappbrowserPlugin.o symbol listing is larger than Node's default 1 MiB buffer.
const NM_MAX_BUFFER = 16 * 1024 * 1024;

function inspectObject(objectFile) {
  try {
    return execFileSync("/usr/bin/nm", [objectFile], {
      encoding: "utf8",
      maxBuffer: NM_MAX_BUFFER,
    });
  } catch (error) {
    const stderr = typeof error.stderr === "string" ? error.stderr : "";
    const stdout = typeof error.stdout === "string" ? error.stdout : "";
    const detail = (stderr || error.message).trim();
    const partial = stdout.trim()
      ? " Partial stdout was ignored because the inspection did not finish."
      : "";
    console.error(`nm failed for ${objectFile}: ${detail}${partial}`);
    process.exit(1);
  }
}

const hits = [];
for (const objectFile of objects) {
  if (inspectObject(objectFile).includes("method_exchangeImplementations")) {
    hits.push(objectFile);
  }
}

const found = hits.length > 0;
if (expectation === "absent" && found) {
  console.error("iOS build still references method_exchangeImplementations:");
  for (const hit of hits) {
    console.error(`  ${hit}`);
  }
  process.exit(1);
}

if (expectation === "present" && !found) {
  console.error(
    `iOS build is missing method_exchangeImplementations (${objects.length} plugin objects scanned).`,
  );
  process.exit(1);
}

console.log(
  `iOS build method_exchangeImplementations ${expectation} (${objects.length} plugin objects scanned).`,
);
