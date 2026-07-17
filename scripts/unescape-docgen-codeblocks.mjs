#!/usr/bin/env bun
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

function addBlankLineBeforeFence(output) {
  const previous = output[output.length - 1];
  if (previous !== undefined && previous.trim() !== '') {
    output.push('');
  }
}

function addBlankLineAfterFence(output, lines, index) {
  const next = lines[index + 1];
  if (next !== undefined && next.trim() !== '' && !FENCE_LINE.test(next)) {
    output.push('');
  }
}

export function ensureBlankLinesAroundFences(lines) {
  const output = [];
  let inFence = false;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    if (!FENCE_LINE.test(line)) {
      output.push(line);
      continue;
    }

    if (!inFence) {
      addBlankLineBeforeFence(output);
    }

    output.push(line);
    inFence = !inFence;

    if (!inFence) {
      addBlankLineAfterFence(output, lines, i);
    }
  }

  return output;
}

function main() {
  const readmePath = join(dirname(fileURLToPath(import.meta.url)), '..', 'README.md');
  const content = readFileSync(readmePath, 'utf8');
  writeFileSync(readmePath, unescapeDocgenCodeBlocks(content));
}

if (import.meta.main) {
  main();
}
