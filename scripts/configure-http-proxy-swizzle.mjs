#!/usr/bin/env node
/**
 * Capacitor `cap sync` hook.
 *
 * Same idea as @capgo/capacitor-social-login's configure-dependencies script:
 * read the app's capacitor.config from CAPACITOR_CONFIG and choose which
 * native implementation is compiled.
 *
 * httpProxySwizzle defaults to true so existing proxy apps keep HTTP/HTTPS
 * interception. false excludes HttpProxySchemeSwizzle.swift and compiles
 * HttpProxySchemeSwizzleStub.swift, which has no method_exchangeImplementations.
 *
 * Capacitor runs this with cwd set to the plugin root.
 */

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const SOURCE_DIR = path.join("ios", "Sources", "InAppBrowserPlugin");
const SWIZZLE_FILE = "HttpProxySchemeSwizzle.swift";
const STUB_FILE = "HttpProxySchemeSwizzleStub.swift";
const BLOCK_PATTERN =
  /^([ \t]*)(\/\/|#) http-proxy-swizzle:begin\r?\n[\s\S]*?^[ \t]*(?:\/\/|#) http-proxy-swizzle:end/m;

const PACKAGE_EXCLUDE = {
  true: `exclude: ["${STUB_FILE}"],`,
  false: `exclude: ["${SWIZZLE_FILE}"],`,
};

const PODSPEC_EXCLUDE = {
  true: `s.exclude_files = '${SOURCE_DIR}/${STUB_FILE}'`,
  false: `s.exclude_files = '${SOURCE_DIR}/${SWIZZLE_FILE}'`,
};

export function isHttpProxySwizzleEnabled(configJson) {
  if (!configJson) {
    return true;
  }

  let config;
  try {
    config = JSON.parse(configJson);
  } catch (error) {
    throw new Error(`CAPACITOR_CONFIG is not valid JSON: ${error.message}`);
  }

  const plugins = config?.plugins ?? {};
  const pluginConfig = plugins.CapgoInAppBrowser ?? plugins.InAppBrowser ?? {};
  return pluginConfig.httpProxySwizzle !== false;
}

export function renderSwizzleBlock(content, enabled, innerLine) {
  const match = BLOCK_PATTERN.exec(content);
  if (!match) {
    throw new Error("missing http-proxy-swizzle markers");
  }

  const indent = match[1];
  const comment = match[2];
  const state = enabled ? "enabled" : "disabled";
  const block = [
    `${indent}${comment} http-proxy-swizzle:begin`,
    `${indent}${comment} http-proxy-swizzle:${state}`,
    `${indent}${innerLine}`,
    `${indent}${comment} http-proxy-swizzle:end`,
  ].join("\n");

  return content.replace(BLOCK_PATTERN, block);
}

function assertSources(root) {
  for (const name of [SWIZZLE_FILE, STUB_FILE]) {
    const fullPath = path.join(root, SOURCE_DIR, name);
    if (!fs.existsSync(fullPath)) {
      throw new Error(`Missing ${fullPath}`);
    }
  }
}

function writeIfChanged(filePath, next) {
  const current = fs.readFileSync(filePath, "utf8");
  if (current === next) {
    return false;
  }
  fs.writeFileSync(filePath, next);
  return true;
}

export function configureHttpProxySwizzle(root, enabled) {
  assertSources(root);
  const packagePath = path.join(root, "Package.swift");
  const podspecPath = path.join(root, "CapgoCapacitorInappbrowser.podspec");
  const wrote = [];

  const packageNext = renderSwizzleBlock(
    fs.readFileSync(packagePath, "utf8"),
    enabled,
    PACKAGE_EXCLUDE[enabled],
  );
  if (writeIfChanged(packagePath, packageNext)) {
    wrote.push("Package.swift");
  }

  const podspecNext = renderSwizzleBlock(
    fs.readFileSync(podspecPath, "utf8"),
    enabled,
    PODSPEC_EXCLUDE[enabled],
  );
  if (writeIfChanged(podspecPath, podspecNext)) {
    wrote.push("CapgoCapacitorInappbrowser.podspec");
  }

  return wrote;
}

function selfTest() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "iab-proxy-swizzle-"));
  const sourceDir = path.join(root, SOURCE_DIR);
  fs.mkdirSync(sourceDir, { recursive: true });
  fs.writeFileSync(path.join(sourceDir, SWIZZLE_FILE), "swizzle\n");
  fs.writeFileSync(path.join(sourceDir, STUB_FILE), "stub\n");
  fs.writeFileSync(
    path.join(root, "Package.swift"),
    `// http-proxy-swizzle:begin\n// http-proxy-swizzle:enabled\nexclude: ["${STUB_FILE}"],\n// http-proxy-swizzle:end\n`,
  );
  fs.writeFileSync(
    path.join(root, "CapgoCapacitorInappbrowser.podspec"),
    `# http-proxy-swizzle:begin\n# http-proxy-swizzle:enabled\ns.exclude_files = '${SOURCE_DIR}/${STUB_FILE}'\n# http-proxy-swizzle:end\n`,
  );

  const disabled = configureHttpProxySwizzle(root, false);
  const packageDisabled = fs.readFileSync(path.join(root, "Package.swift"), "utf8");
  const podspecDisabled = fs.readFileSync(path.join(root, "CapgoCapacitorInappbrowser.podspec"), "utf8");
  const enabledAgain = configureHttpProxySwizzle(root, true);
  const packageEnabled = fs.readFileSync(path.join(root, "Package.swift"), "utf8");

  const ok =
    disabled.length === 2 &&
    packageDisabled.includes(`exclude: ["${SWIZZLE_FILE}"]`) &&
    packageDisabled.includes("http-proxy-swizzle:disabled") &&
    podspecDisabled.includes(`${SOURCE_DIR}/${SWIZZLE_FILE}`) &&
    enabledAgain.length === 2 &&
    packageEnabled.includes(`exclude: ["${STUB_FILE}"]`) &&
    packageEnabled.includes("http-proxy-swizzle:enabled") &&
    isHttpProxySwizzleEnabled("") === true &&
    isHttpProxySwizzleEnabled('{"plugins":{"CapgoInAppBrowser":{"httpProxySwizzle":false}}}') === false &&
    isHttpProxySwizzleEnabled('{"plugins":{"InAppBrowser":{"httpProxySwizzle":false}}}') === false &&
    configureHttpProxySwizzle(root, true).length === 0;

  fs.rmSync(root, { recursive: true, force: true });
  if (!ok) {
    console.error("HTTP proxy swizzle hook self-test failed.");
    process.exit(1);
  }
  console.log("HTTP proxy swizzle hook self-test passed.");
}

function main() {
  const enabled = isHttpProxySwizzleEnabled(process.env.CAPACITOR_CONFIG);
  const wrote = configureHttpProxySwizzle(process.cwd(), enabled);
  const state = enabled ? "included" : "omitted";
  const files = wrote.length > 0 ? wrote.join(", ") : "already up to date";
  console.log(`[InAppBrowser] HTTP proxy swizzle ${state} (${files}).`);
}

const isDirect =
  process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isDirect) {
  if (process.argv.includes("--self-test")) {
    selfTest();
  } else {
    try {
      main();
    } catch (error) {
      console.error(`[InAppBrowser] ${error.message}`);
      process.exit(1);
    }
  }
}
