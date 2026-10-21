import { describe, expect, it } from 'bun:test';

import { CAPGO_CONSOLE_POST_TYPE, normalizeConsoleLevel, parseCapgoConsolePostMessage } from './web-console-capture';

describe('web console capture', () => {
  it('parses console postMessage payloads', () => {
    const parsed = parseCapgoConsolePostMessage({
      type: CAPGO_CONSOLE_POST_TYPE,
      level: 'warn',
      message: 'popup blocked',
      source: 'https://example.com/app.js',
      line: 12,
      column: 3,
    });
    expect(parsed).toEqual({
      level: 'warn',
      message: 'popup blocked',
      source: 'https://example.com/app.js',
      line: 12,
      column: 3,
    });
  });

  it('ignores unrelated postMessage payloads', () => {
    expect(parseCapgoConsolePostMessage({ type: 'other', message: 'x' })).toBeNull();
    expect(parseCapgoConsolePostMessage(null)).toBeNull();
  });

  it('normalizes Android-style levels', () => {
    expect(normalizeConsoleLevel('TIP')).toBe('info');
    expect(normalizeConsoleLevel('WARNING')).toBe('warn');
    expect(normalizeConsoleLevel('ASSERT')).toBe('error');
  });
});
