import fs from "node:fs";
import path from "node:path";

export const DEFAULT_SKIP_DIRS = new Set([
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

function resolveUnderRoot(p, rootDir) {
  const filePath = path.resolve(p);
  if (!rootDir) {
    return filePath;
  }
  const root = path.resolve(rootDir);
  const rel = path.relative(root, filePath);
  if (rel.startsWith("..") || path.isAbsolute(rel)) {
    return null;
  }
  return filePath;
}

export function readText(p, rootDir) {
  const filePath = resolveUnderRoot(p, rootDir);
  if (!filePath) {
    return "";
  }
  try {
    return fs.readFileSync(filePath, "utf8");
  } catch {
    return "";
  }
}

export function exists(p, rootDir) {
  const filePath = resolveUnderRoot(p, rootDir);
  if (!filePath) {
    return false;
  }
  try {
    fs.accessSync(filePath);
    return true;
  } catch {
    return false;
  }
}

export function parseArgs(argv) {
  const out = { dir: process.cwd() };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--dir" || a === "--pluginDir") {
      out.dir = path.resolve(argv[++i] || ".");
    }
  }
  return out;
}

function pushDirEntry(stack, dir, skipDirs, e) {
  if (e.isDirectory()) {
    if (skipDirs.has(e.name)) {
      return;
    }
    stack.push(path.join(dir, e.name));
    return;
  }
  if (!e.isFile()) {
    return;
  }
}

export function walkFiles(rootDir, exts, skipDirs = DEFAULT_SKIP_DIRS) {
  const out = [];
  const stack = [rootDir];
  while (stack.length) {
    const dir = stack.pop();
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const e of entries) {
      if (e.isDirectory()) {
        pushDirEntry(stack, dir, skipDirs, e);
        continue;
      }
      if (!e.isFile()) {
        continue;
      }
      for (const ext of exts) {
        if (e.name.endsWith(ext)) {
          out.push(path.join(dir, e.name));
          break;
        }
      }
    }
  }
  out.sort((a, b) => a.localeCompare(b));
  return out;
}
