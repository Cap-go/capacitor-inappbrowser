#!/usr/bin/env node
/**
 * App Store guideline 2.5.2 guard for iOS plugin sources.
 *
 * Blocks dynamic selector/class lookup and runtime-built API names.
 * Allows documented private runtime calls when marked with:
 *   // appstore-2.5.2-allow: <reason>
 */

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const IOS_SOURCES = path.join(ROOT, "ios", "Sources");

const SKIP_DIRS = new Set([
  "node_modules",
  "dist",
  "build",
  ".build",
  ".gradle",
  "Pods",
  "DerivedData",
  ".swiftpm",
  ".git",
]);

/** Always forbidden in iOS plugin sources. */
const FORBIDDEN_PATTERNS = [
  { id: "NSSelectorFromString", pattern: /\bNSSelectorFromString\s*\(/ },
  { id: "performSelector", pattern: /\bperformSelector\b/ },
  { id: "NSClassFromString", pattern: /\bNSClassFromString\s*\(/ },
  { id: "dlopen", pattern: /\bdlopen\s*\(/ },
  { id: "dlsym", pattern: /\bdlsym\s*\(/ },
  { id: "Selector(non-literal)", pattern: /\bSelector\s*\(\s*[^#"\s]/ },
  { id: "Selector(concat)", pattern: /\bSelector\s*\([^)]*\+/ },
  { id: "Selector(interpolation)", pattern: /\bSelector\s*\([^)]*\\\(/ },
];

/** Allowed only with appstore-2.5.2-allow on the same or previous non-empty line. */
const ALLOW_ANNOTATED_PATTERNS = [
  { id: "method_exchangeImplementations", pattern: /\bmethod_exchangeImplementations\s*\(/ },
  { id: "class_getClassMethod", pattern: /\bclass_getClassMethod\s*\(/ },
  { id: "class_getInstanceMethod", pattern: /\bclass_getInstanceMethod\s*\(/ },
  { id: "method_getImplementation", pattern: /\bmethod_getImplementation\s*\(/ },
  { id: "method_setImplementation", pattern: /\bmethod_setImplementation\s*\(/ },
  { id: "unsafeBitCast", pattern: /\bunsafeBitCast\s*\(/ },
  { id: "objc_getAssociatedObject", pattern: /\bobjc_getAssociatedObject\s*\(/ },
  { id: "objc_setAssociatedObject", pattern: /\bobjc_setAssociatedObject\s*\(/ },
];

const ALLOW_TAG = /appstore-2\.5\.2-allow:\s*\S/;

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name)) {
      continue;
    }
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath, files);
    } else if (
      entry.name.endsWith(".swift") ||
      entry.name.endsWith(".m") ||
      entry.name.endsWith(".mm")
    ) {
      files.push(fullPath);
    }
  }
  return files;
}

function lineHasAllow(lines, index) {
  const same = lines[index] ?? "";
  if (ALLOW_TAG.test(same)) {
    return true;
  }
  for (let i = index - 1; i >= 0 && i >= index - 2; i -= 1) {
    const candidate = lines[i].trim();
    if (!candidate) {
      continue;
    }
    return ALLOW_TAG.test(candidate);
  }
  return false;
}

function lineNumberAtIndex(text, index) {
  return text.slice(0, index).split(/\r?\n/).length;
}

function scanForbiddenAcrossFile(text, filePath) {
  const violations = [];

  for (const rule of FORBIDDEN_PATTERNS) {
    const pattern = new RegExp(rule.pattern.source, rule.pattern.flags.includes("g") ? rule.pattern.flags : `${rule.pattern.flags}g`);
    for (const match of text.matchAll(pattern)) {
      const lineNumber = lineNumberAtIndex(text, match.index ?? 0);
      const line = (text.split(/\r?\n/)[lineNumber - 1] ?? "").trim();
      violations.push({
        filePath,
        lineNumber,
        rule: rule.id,
        message: `${rule.id} is not allowed (use static #selector / static let constants)`,
        line,
      });
    }
  }

  return violations;
}

function scanAnnotatedAcrossFile(text, filePath, lines) {
  const violations = [];

  for (const rule of ALLOW_ANNOTATED_PATTERNS) {
    const pattern = new RegExp(
      rule.pattern.source,
      rule.pattern.flags.includes("g") ? rule.pattern.flags : `${rule.pattern.flags}g`,
    );
    for (const match of text.matchAll(pattern)) {
      const lineNumber = lineNumberAtIndex(text, match.index ?? 0);
      const lineIndex = lineNumber - 1;
      if (lineHasAllow(lines, lineIndex)) {
        continue;
      }
      const line = (lines[lineIndex] ?? "").trim();
      violations.push({
        filePath,
        lineNumber,
        rule: rule.id,
        message: `${rule.id} requires // appstore-2.5.2-allow: <reason> on this or the previous line`,
        line,
      });
    }
  }

  return violations;
}

function scanFile(filePath) {
  const text = fs.readFileSync(filePath, "utf8");
  const lines = text.split(/\r?\n/);
  const violations = scanForbiddenAcrossFile(text, filePath);
  violations.push(...scanAnnotatedAcrossFile(text, filePath, lines));

  return violations;
}

function main() {
  if (!fs.existsSync(IOS_SOURCES)) {
    console.log("No ios/Sources directory; skipping App Store 2.5.2 iOS guard.");
    return;
  }

  const files = walk(IOS_SOURCES);
  const violations = files.flatMap(scanFile);

  if (violations.length === 0) {
    console.log(`App Store 2.5.2 iOS guard passed (${files.length} files scanned).`);
    return;
  }

  console.error("App Store 2.5.2 iOS guard failed:\n");
  for (const violation of violations) {
    const relative = path.relative(ROOT, violation.filePath);
    console.error(`${relative}:${violation.lineNumber}: ${violation.message}`);
    console.error(`  ${violation.line.trim()}`);
  }
  process.exit(1);
}

main();
