import { describe, expect, it } from 'bun:test';

import {
  buildConsoleCaptureScript,
  CAPGO_CONSOLE_POST_TYPE,
  normalizeConsoleLevel,
  parseCapgoConsolePostMessage,
} from './web-console-capture';

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
    expect(normalizeConsoleLevel('unexpected')).toBe('log');
  });

  it('rejects invalid console payloads', () => {
    expect(parseCapgoConsolePostMessage({ type: CAPGO_CONSOLE_POST_TYPE, level: 'log', message: 1 })).toBeNull();
    expect(
      parseCapgoConsolePostMessage({
        type: CAPGO_CONSOLE_POST_TYPE,
        level: 'warn',
        message: 'ok',
        line: '12',
        column: '3',
      }),
    ).toEqual({
      level: 'warn',
      message: 'ok',
      line: 12,
      column: 3,
    });
  });

  it('builds an injection script with console hooks', () => {
    const script = buildConsoleCaptureScript();
    expect(script).toContain(CAPGO_CONSOLE_POST_TYPE);
    expect(script).toContain('console.assert');
  });
});
