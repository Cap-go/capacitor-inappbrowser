#!/usr/bin/env node
/**
 * App Store guideline 2.5.2 guard for iOS plugin sources.
 *
 * Blocks dynamic selector/class lookup and runtime-built API names.
 * Allows documented private runtime calls when marked with:
 *   // appstore-2.5.2-allow: <reason>
 *
 * `method_exchangeImplementations` is compiled only inside:
 *   #if CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE
 * Package.swift and the podspec must keep that flag off unless the app opts in.
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
const HTTP_PROXY_SWIZZLE_FLAG = "CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE";
const HTTP_PROXY_SWIZZLE_IF = new RegExp(
  `^\\s*#\\s*if\\s+${HTTP_PROXY_SWIZZLE_FLAG}\\s*(?:\\/\\/.*)?$`,
);
const METHOD_EXCHANGE = /\bmethod_exchangeImplementations\s*\(/;

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
  violations.push(...scanMethodExchangeOptIn(text, filePath));

  return violations;
}

function scanMethodExchangeOptIn(text, filePath) {
  const lines = text.split(/\r?\n/);
  const violations = [];
  let depth = 0;
  let optInDepth = null;

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    if (/^\s*#\s*if\b/.test(line)) {
      depth += 1;
      if (optInDepth === null && HTTP_PROXY_SWIZZLE_IF.test(line)) {
        optInDepth = depth;
      }
    }

    if (METHOD_EXCHANGE.test(line) && optInDepth === null) {
      violations.push({
        filePath,
        lineNumber: index + 1,
        rule: "method_exchangeImplementations",
        message: `${HTTP_PROXY_SWIZZLE_FLAG} must wrap method_exchangeImplementations so default builds omit it`,
        line: line.trim(),
      });
    }

    if (/^\s*#\s*endif\b/.test(line)) {
      if (optInDepth === depth) {
        optInDepth = null;
      }
      depth = Math.max(0, depth - 1);
    }
  }

  return violations;
}

function isManifestComment(line) {
  const trimmed = line.trim();
  return trimmed.startsWith("//") || trimmed.startsWith("#");
}

function scanOptInManifests() {
  const violations = [];
  const packageSwiftPath = path.join(ROOT, "Package.swift");
  const podspecPath = path.join(ROOT, "CapgoCapacitorInappbrowser.podspec");
  const packageSwift = fs.readFileSync(packageSwiftPath, "utf8");
  const podspec = fs.readFileSync(podspecPath, "utf8");

  const assignment = packageSwift.match(/let enableHttpProxySwizzle\s*=\s*([^\n]+)/);
  if (
    !assignment ||
    !assignment[1].includes(`environment["${HTTP_PROXY_SWIZZLE_FLAG}"]`) ||
    !assignment[1].includes('== "1"')
  ) {
    violations.push({
      filePath: packageSwiftPath,
      lineNumber: 1,
      rule: HTTP_PROXY_SWIZZLE_FLAG,
      message: `Package.swift must set enableHttpProxySwizzle from ${HTTP_PROXY_SWIZZLE_FLAG}=1`,
      line: assignment ? assignment[0] : "missing enableHttpProxySwizzle",
    });
  }

  if (/enableHttpProxySwizzle\s*=\s*true/.test(packageSwift)) {
    violations.push({
      filePath: packageSwiftPath,
      lineNumber: 1,
      rule: HTTP_PROXY_SWIZZLE_FLAG,
      message: "Package.swift must not force the HTTP proxy swizzle on",
      line: "enableHttpProxySwizzle = true",
    });
  }

  const packageLines = packageSwift.split(/\r?\n/);
  for (let index = 0; index < packageLines.length; index += 1) {
    const line = packageLines[index];
    if (!line.includes(HTTP_PROXY_SWIZZLE_FLAG) || isManifestComment(line)) {
      continue;
    }
    const previous = packageLines[index - 1] ?? "";
    const isEnvAssignment = line.includes("enableHttpProxySwizzle") && line.includes("environment");
    const isConditionalDefine =
      line.includes("enableHttpProxySwizzle ?") ||
      (previous.includes("enableHttpProxySwizzle") && line.includes(".define("));
    if (!isEnvAssignment && !isConditionalDefine) {
      violations.push({
        filePath: packageSwiftPath,
        lineNumber: index + 1,
        rule: HTTP_PROXY_SWIZZLE_FLAG,
        message: "HTTP proxy swizzle flag must stay behind enableHttpProxySwizzle",
        line: line.trim(),
      });
    }
  }

  const podLines = podspec.split(/\r?\n/);
  let envIfDepth = 0;
  let sawEnvIf = false;
  for (let index = 0; index < podLines.length; index += 1) {
    const line = podLines[index];
    if (line.includes(`ENV['${HTTP_PROXY_SWIZZLE_FLAG}'] == '1'`)) {
      sawEnvIf = true;
      envIfDepth += 1;
    }
    if (line.includes(HTTP_PROXY_SWIZZLE_FLAG) && envIfDepth === 0 && !isManifestComment(line)) {
      violations.push({
        filePath: podspecPath,
        lineNumber: index + 1,
        rule: HTTP_PROXY_SWIZZLE_FLAG,
        message: "podspec must set the HTTP proxy swizzle flag only inside the ENV opt-in",
        line: line.trim(),
      });
    }
    if (envIfDepth > 0 && /^\s*end\s*$/.test(line)) {
      envIfDepth -= 1;
    }
  }
  if (!sawEnvIf) {
    violations.push({
      filePath: podspecPath,
      lineNumber: 1,
      rule: HTTP_PROXY_SWIZZLE_FLAG,
      message: `podspec must opt in with ENV['${HTTP_PROXY_SWIZZLE_FLAG}'] == '1'`,
      line: "missing ENV opt-in",
    });
  }

  return violations;
}

function selfTestOptInScanner() {
  const optedIn = scanMethodExchangeOptIn(
    ["#if CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE", "method_exchangeImplementations(original, swizzled)", "#endif"].join("\n"),
    "opt.swift",
  );
  const leaked = scanMethodExchangeOptIn("method_exchangeImplementations(original, swizzled)\n", "leak.swift");
  const alwaysOn = scanMethodExchangeOptIn(
    ["#if CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE || true", "method_exchangeImplementations(original, swizzled)", "#endif"].join("\n"),
    "always.swift",
  );
  if (optedIn.length !== 0 || leaked.length !== 1 || alwaysOn.length !== 1) {
    console.error("App Store 2.5.2 opt-in scanner self-test failed.");
    process.exit(1);
  }
}

function main() {
  selfTestOptInScanner();

  if (!fs.existsSync(IOS_SOURCES)) {
    console.log("No ios/Sources directory; skipping App Store 2.5.2 iOS guard.");
    return;
  }

  const files = walk(IOS_SOURCES);
  const violations = files.flatMap(scanFile);
  violations.push(...scanOptInManifests());

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
