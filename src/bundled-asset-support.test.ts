import { describe, expect, it } from 'bun:test';

import {
  containsPathTraversal,
  handlerSchemeForPlatform,
  isAbsoluteUrl,
  isBundledLocalUrl,
  isRelativeBundledPath,
  normalizeBundledPath,
  parseBundledLocalConfig,
  resolveBundledAssetUrl,
  resolveLegacyNativeWebViewUrl,
} from './bundled-asset-support';

describe('bundled asset path helpers', () => {
  it('detects absolute urls', () => {
    expect(isAbsoluteUrl('https://example.com')).toBe(true);
    expect(isAbsoluteUrl('capacitor://localhost/index.html')).toBe(true);
    expect(isAbsoluteUrl('data:text/html,<h1>hi</h1>')).toBe(true);
    expect(isAbsoluteUrl('/page.html')).toBe(false);
    expect(isAbsoluteUrl('page.html')).toBe(false);
  });

  it('detects relative bundled paths', () => {
    expect(isRelativeBundledPath('/page.html')).toBe(true);
    expect(isRelativeBundledPath('assets/app.js')).toBe(true);
    expect(isRelativeBundledPath('https://example.com')).toBe(false);
    expect(isRelativeBundledPath('')).toBe(false);
  });

  it('normalizes bundled paths', () => {
    expect(normalizeBundledPath('page.html')).toBe('/page.html');
    expect(normalizeBundledPath('/nested/page.html')).toBe('/nested/page.html');
    expect(normalizeBundledPath('/')).toBe('/');
  });

  it('rejects path traversal segments', () => {
    expect(containsPathTraversal('/../secret.txt')).toBe(true);
    expect(normalizeBundledPath('/../secret.txt')).toBeNull();
    expect(resolveBundledAssetUrl('/../secret.txt', 'ios')).toBeNull();
  });
});

describe('bundled asset url resolution', () => {
  it('resolves relative paths for ios', () => {
    expect(resolveBundledAssetUrl('/page.html', 'ios')).toEqual({
      url: 'capacitor://localhost/page.html',
    });
    expect(resolveBundledAssetUrl('assets/app.js', 'ios')).toEqual({
      url: 'capacitor://localhost/assets/app.js',
    });
  });

  it('uses capacitor scheme when ios local config uses http', () => {
    const localConfig = parseBundledLocalConfig('http://localhost/');
    expect(localConfig).not.toBeNull();
    if (!localConfig) {
      return;
    }
    expect(handlerSchemeForPlatform('ios', localConfig)).toBe('capacitor');
    expect(resolveBundledAssetUrl('/page.html', 'ios', localConfig)).toEqual({
      url: 'capacitor://localhost/page.html',
    });
  });

  it('resolves relative paths for android', () => {
    expect(resolveBundledAssetUrl('/page.html', 'android')).toEqual({
      url: 'https://localhost/page.html',
    });
  });

  it('preserves http scheme for android when configured', () => {
    const localConfig = parseBundledLocalConfig('http://localhost/');
    expect(localConfig).not.toBeNull();
    if (!localConfig) {
      return;
    }
    expect(handlerSchemeForPlatform('android', localConfig)).toBe('http');
    expect(resolveBundledAssetUrl('/page.html', 'android', localConfig)).toEqual({
      url: 'http://localhost/page.html',
    });
  });

  it('keeps remote urls unchanged', () => {
    expect(resolveBundledAssetUrl('https://example.com/page.html', 'ios')).toEqual({
      url: 'https://example.com/page.html',
    });
    expect(resolveBundledAssetUrl('http://example.com:3000', 'android')).toEqual({
      url: 'http://example.com:3000',
    });
  });

  it('recognizes bundled local urls', () => {
    expect(resolveBundledAssetUrl('capacitor://localhost/index.html', 'ios')).toEqual({
      url: 'capacitor://localhost/index.html',
    });
    expect(resolveBundledAssetUrl('https://localhost/index.html', 'android')).toEqual({
      url: 'https://localhost/index.html',
    });
    expect(
      isBundledLocalUrl('http://localhost:3000/index.html', 'android', { scheme: 'https', host: 'localhost' }),
    ).toBe(false);
    expect(isBundledLocalUrl('capacitor://localhost/index.html', 'ios', { scheme: 'https', host: 'localhost' })).toBe(
      true,
    );
  });

  it('rewrites bundled local urls to the handler scheme on ios', () => {
    const localConfig = parseBundledLocalConfig('https://localhost/');
    expect(localConfig).not.toBeNull();
    if (!localConfig) {
      return;
    }
    expect(resolveBundledAssetUrl('https://localhost/index.html', 'ios', localConfig)).toEqual({
      url: 'capacitor://localhost/index.html',
    });
    expect(resolveBundledAssetUrl('https://user:pass@localhost/secure/index.html', 'ios', localConfig)).toEqual({
      url: 'capacitor://user:pass@localhost/secure/index.html',
    });
  });

  it('uses custom local config when provided', () => {
    const localConfig = parseBundledLocalConfig('https://example.com/');
    expect(localConfig).toEqual({ scheme: 'https', host: 'example.com' });
    expect(isBundledLocalUrl('https://example.com/app/index.html', 'android', localConfig)).toBe(true);
    expect(resolveBundledAssetUrl('/app/index.html', 'android', localConfig)).toEqual({
      url: 'https://example.com/app/index.html',
    });
  });
});

describe('legacy native webview url fallback', () => {
  it('rewrites relative bundled paths for legacy native resolution', () => {
    expect(resolveLegacyNativeWebViewUrl('/index.html', 'ios')).toBe('capacitor://localhost/index.html');
    expect(resolveLegacyNativeWebViewUrl('assets/app.js', 'android')).toBe('https://localhost/assets/app.js');
  });

  it('keeps remote urls unchanged for legacy native resolution', () => {
    expect(resolveLegacyNativeWebViewUrl('https://example.com/page.html', 'ios')).toBe('https://example.com/page.html');
  });

  it('rejects path traversal for legacy native resolution', () => {
    expect(() => resolveLegacyNativeWebViewUrl('/../secret.txt', 'ios')).toThrow('Invalid bundled asset path');
  });
});
