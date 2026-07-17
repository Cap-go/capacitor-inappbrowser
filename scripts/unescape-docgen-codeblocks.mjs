import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const readmePath = join(dirname(fileURLToPath(import.meta.url)), '..', 'README.md');
let content = readFileSync(readmePath, 'utf8');

content = content.replace(/(```[^\n]*\n)([\s\S]*?)(```)/g, (match, open, body, close) => {
  const unescaped = body.replace(/&lt;/g, '<').replace(/&gt;/g, '>');
  return open + unescaped + close;
});

writeFileSync(readmePath, content);
