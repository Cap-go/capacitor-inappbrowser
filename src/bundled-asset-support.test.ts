import { describe, expect, it } from 'bun:test';

import {
  isAbsoluteUrl,
  isBundledLocalUrl,
  isRelativeBundledPath,
  normalizeBundledPath,
  parseBundledLocalConfig,
  resolveBundledAssetUrl,
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
});

describe('bundled asset url resolution', () => {
  it('resolves relative paths for ios', () => {
    expect(resolveBundledAssetUrl('/page.html', 'ios')).toEqual({
      url: 'capacitor://localhost/page.html',
      needsHandler: true,
    });
    expect(resolveBundledAssetUrl('assets/app.js', 'ios')).toEqual({
      url: 'capacitor://localhost/assets/app.js',
      needsHandler: true,
    });
  });

  it('resolves relative paths for android', () => {
    expect(resolveBundledAssetUrl('/page.html', 'android')).toEqual({
      url: 'https://localhost/page.html',
      needsHandler: true,
    });
  });

  it('keeps remote urls unchanged', () => {
    expect(resolveBundledAssetUrl('https://example.com/page.html', 'ios')).toEqual({
      url: 'https://example.com/page.html',
      needsHandler: false,
    });
    expect(resolveBundledAssetUrl('http://localhost:3000', 'android')).toEqual({
      url: 'http://localhost:3000',
      needsHandler: false,
    });
  });

  it('recognizes bundled local urls', () => {
    expect(resolveBundledAssetUrl('capacitor://localhost/index.html', 'ios')).toEqual({
      url: 'capacitor://localhost/index.html',
      needsHandler: true,
    });
    expect(resolveBundledAssetUrl('https://localhost/index.html', 'android')).toEqual({
      url: 'https://localhost/index.html',
      needsHandler: true,
    });
  });

  it('uses custom local config when provided', () => {
    const localConfig = parseBundledLocalConfig('https://example.com/');
    expect(localConfig).toEqual({ scheme: 'https', host: 'example.com' });
    expect(isBundledLocalUrl('https://example.com/app/index.html', localConfig)).toBe(true);
    expect(resolveBundledAssetUrl('/app/index.html', 'android', localConfig)).toEqual({
      url: 'https://example.com/app/index.html',
      needsHandler: true,
    });
  });
});
