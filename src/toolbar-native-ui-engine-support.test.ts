import { describe, expect, test } from 'bun:test';

import { shouldRegisterNativeUIEngineToolbar, validateNativeUIEngineOptions } from './toolbar-native-ui-engine-support';

describe('toolbar native ui engine gating', () => {
  test('off path does not register', () => {
    expect(shouldRegisterNativeUIEngineToolbar(false, '')).toBe(false);
    expect(shouldRegisterNativeUIEngineToolbar(false, 'navigation')).toBe(false);
  });

  test('blank toolbar never registers', () => {
    expect(shouldRegisterNativeUIEngineToolbar(true, 'blank')).toBe(false);
    expect(shouldRegisterNativeUIEngineToolbar(true, 'BLANK')).toBe(true);
  });

  test('selector required when on', () => {
    expect(validateNativeUIEngineOptions(false, null)).toBeNull();
    expect(validateNativeUIEngineOptions(true, null)).toBe(
      'nativeUIProjectionSelector is required when enableNativeUIEngine is true',
    );
    expect(validateNativeUIEngineOptions(true, '#slot')).toBeNull();
    expect(validateNativeUIEngineOptions(true, 'div#slot')).toBeNull();
  });
});
