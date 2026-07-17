#!/usr/bin/env node
/**
 * Restore literal angle brackets inside markdown fenced code blocks.
 *
 * @capacitor/docgen escapes `<` and `>` in JSDoc descriptions, which breaks
 * HTML/XML examples rendered on GitHub inside ``` fences.
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const CODE_FENCE_PATTERN = /(```[^\n]*\n)([\s\S]*?)(```)/g;

function unescapeCodeBlockEntities(body) {
  return body.replaceAll('&lt;', '<').replaceAll('&gt;', '>');
}

function unescapeDocgenCodeBlocks(content) {
  return content.replace(CODE_FENCE_PATTERN, (match, open, body, close) => {
    return `${open}${unescapeCodeBlockEntities(body)}${close}`;
  });
}

function main() {
  const readmePath = join(dirname(fileURLToPath(import.meta.url)), '..', 'README.md');
  const content = readFileSync(readmePath, 'utf8');
  writeFileSync(readmePath, unescapeDocgenCodeBlocks(content));
}

main();
