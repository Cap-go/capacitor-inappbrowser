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
  let root;
  let resolvedPath;
  try {
    root = fs.realpathSync.native(rootDir);
    resolvedPath = fs.realpathSync.native(filePath);
  } catch {
    return null;
  }
  const rel = path.relative(root, resolvedPath);
  if (rel.startsWith("..") || path.isAbsolute(rel)) {
    return null;
  }
  return resolvedPath;
}

export function readText(p, rootDir) {
  const filePath = resolveUnderRoot(p, rootDir);
  if (!filePath) {
    return "";
  }
  try {
    return fs.readFileSync(filePath, "utf8"); // NOSONAR S8707 - path bounded by resolveUnderRoot
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
    fs.accessSync(filePath); // NOSONAR S8707 - path bounded by resolveUnderRoot
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

function walkDirEntries(safeDir, stack, skipDirs, exts, out) {
  let entries;
  try {
    entries = fs.readdirSync(safeDir, { withFileTypes: true }); // NOSONAR S8707 - safeDir validated under root
  } catch {
    return;
  }
  for (const e of entries) {
    if (e.isDirectory()) {
      if (!skipDirs.has(e.name)) {
        stack.push(path.join(safeDir, e.name));
      }
      continue;
    }
    if (!e.isFile()) {
      continue;
    }
    const filePath = path.join(safeDir, e.name);
    for (const ext of exts) {
      if (e.name.endsWith(ext)) {
        out.push(filePath);
        break;
      }
    }
  }
}

export function walkFiles(rootDir, exts, skipDirs = DEFAULT_SKIP_DIRS) {
  const out = [];
  const root = path.resolve(rootDir);
  const stack = [root];
  while (stack.length) {
    const dir = stack.pop();
    const safeDir = resolveUnderRoot(dir, root);
    if (!safeDir) {
      continue;
    }
    walkDirEntries(safeDir, stack, skipDirs, exts, out);
  }
  out.sort((a, b) => a.localeCompare(b));
  return out;
}
