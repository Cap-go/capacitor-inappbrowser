export function shouldRegisterNativeUIEngineToolbar(enabled: boolean, toolbarType: string): boolean {
  if (!enabled) {
    return false;
  }
  const trimmed = toolbarType.trim();
  if (trimmed.length === 0) {
    return true;
  }
  return trimmed !== 'blank';
}

export function validateNativeUIEngineOptions(
  enabled: boolean,
  projectionSelector?: string | null,
): string | null {
  if (!enabled) {
    return null;
  }
  if (!projectionSelector || projectionSelector.trim() === '') {
    return 'nativeUIProjectionSelector is required when enableNativeUIEngine is true';
  }
  return null;
}