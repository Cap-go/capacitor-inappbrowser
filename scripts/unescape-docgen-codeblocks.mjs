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

const FENCE_LINE = /^```/;

export function unescapeCodeBlockEntities(text) {
  return text.replaceAll('&lt;', '<').replaceAll('&gt;', '>');
}

export function unescapeDocgenCodeBlocks(content) {
  const lines = content.split('\n');
  const output = [];
  let inFence = false;

  for (const line of lines) {
    if (FENCE_LINE.test(line)) {
      inFence = !inFence;
      output.push(line);
      continue;
    }

    output.push(inFence ? unescapeCodeBlockEntities(line) : line);
  }

  return ensureBlankLinesAroundFences(output).join('\n');
}

export function ensureBlankLinesAroundFences(lines) {
  const output = [];
  let inFence = false;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    if (FENCE_LINE.test(line)) {
      if (!inFence) {
        const previous = output[output.length - 1];
        if (previous !== undefined && previous.trim() !== '') {
          output.push('');
        }
      }

      output.push(line);
      inFence = !inFence;

      if (!inFence) {
        const next = lines[i + 1];
        if (next !== undefined && next.trim() !== '' && !FENCE_LINE.test(next)) {
          output.push('');
        }
      }

      continue;
    }

    output.push(line);
  }

  return output;
}

function main() {
  const readmePath = join(dirname(fileURLToPath(import.meta.url)), '..', 'README.md');
  const content = readFileSync(readmePath, 'utf8');
  writeFileSync(readmePath, unescapeDocgenCodeBlocks(content));
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  main();
}
