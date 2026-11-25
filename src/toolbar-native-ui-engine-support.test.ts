import { describe, expect, test } from 'bun:test';

const shouldRegister = (enabled: boolean, toolbarType: string) => {
  if (!enabled) {
    return false;
  }
  return toolbarType.toLowerCase() !== 'blank';
};

const validateOptions = (enabled: boolean, projectionSelector?: string | null) => {
  if (!enabled) {
    return null;
  }
  if (!projectionSelector || projectionSelector.trim() === '') {
    return 'nativeUIProjectionSelector is required when enableNativeUIEngine is true';
  }
  const trimmed = projectionSelector.trim();
  if (!trimmed.startsWith('#') && !trimmed.startsWith('.') && !trimmed.startsWith('[')) {
    return 'nativeUIProjectionSelector must be a CSS selector';
  }
  return null;
};

describe('toolbar native ui engine gating', () => {
  test('off path does not register', () => {
    expect(shouldRegister(false, '')).toBe(false);
    expect(shouldRegister(false, 'navigation')).toBe(false);
  });

  test('blank toolbar never registers', () => {
    expect(shouldRegister(true, 'blank')).toBe(false);
  });

  test('selector required when on', () => {
    expect(validateOptions(false, null)).toBeNull();
    expect(validateOptions(true, null)).toBe(
      'nativeUIProjectionSelector is required when enableNativeUIEngine is true',
    );
    expect(validateOptions(true, '#slot')).toBeNull();
  });
});
