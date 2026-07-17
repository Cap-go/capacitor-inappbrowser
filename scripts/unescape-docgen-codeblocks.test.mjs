import { describe, expect, test } from 'bun:test';
import { unescapeCodeBlockEntities, unescapeDocgenCodeBlocks } from './unescape-docgen-codeblocks.mjs';

describe('unescape docgen code blocks', () => {
  test('decodes entities inside a single fenced block', () => {
    const input = ['Before', '```html', '&lt;html&gt;', '```', 'After'].join('\n');
    const expected = ['Before', '', '```html', '<html>', '```', '', 'After'].join('\n');

    expect(unescapeDocgenCodeBlocks(input)).toBe(expected);
  });

  test('leaves entities outside fenced blocks unchanged', () => {
    const input = ['**Returns:** <code>Promise&lt;void&gt;</code>', '```html', '&lt;body&gt;', '```'].join('\n');
    const expected = ['**Returns:** <code>Promise&lt;void&gt;</code>', '', '```html', '<body>', '```'].join('\n');

    expect(unescapeDocgenCodeBlocks(input)).toBe(expected);
  });

  test('handles multiple adjacent fenced blocks', () => {
    const input = [
      '```html',
      '&lt;html&gt;',
      '```',
      '```xml',
      '&lt;activity /&gt;',
      '```',
      'outside &lt;title&gt;',
    ].join('\n');
    const expected = [
      '```html',
      '<html>',
      '```',
      '',
      '```xml',
      '<activity />',
      '```',
      '',
      'outside &lt;title&gt;',
    ].join('\n');

    expect(unescapeDocgenCodeBlocks(input)).toBe(expected);
  });

  test('unescapes line entities directly', () => {
    expect(unescapeCodeBlockEntities('&lt;key&gt;')).toBe('<key>');
  });

  test('adds blank lines around fenced blocks for markdownlint MD031', () => {
    const input = ['Something like:', '```html', '&lt;html&gt;', '```', 'For mobile:'].join('\n');
    const expected = ['Something like:', '', '```html', '<html>', '```', '', 'For mobile:'].join('\n');

    expect(unescapeDocgenCodeBlocks(input)).toBe(expected);
  });
});
