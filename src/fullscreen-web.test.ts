import { describe, expect, it } from 'bun:test';

import { InAppBrowserWeb } from './web';

describe('fullscreen on Web', () => {
  const browser = new InAppBrowserWeb();

  it('rejects fullscreen opening instead of reporting a successful native presentation', async () => {
    await expect(browser.openWebView({ url: 'https://example.com', fullscreen: true })).rejects.toMatchObject({
      code: 'UNIMPLEMENTED',
    });
  });

  it('preserves ordinary Web opening when fullscreen is omitted or disabled', async () => {
    for (const fullscreen of [undefined, false]) {
      const options = { url: 'https://example.com', fullscreen };
      await expect(browser.openWebView(options)).resolves.toEqual(options);
    }
  });

  it('rejects runtime entry and exit rather than silently changing no state', async () => {
    for (const enabled of [true, false]) {
      await expect(browser.setFullscreen({ enabled, id: 'webview' })).rejects.toMatchObject({
        code: 'UNIMPLEMENTED',
      });
    }
  });

  it('rejects state queries instead of implying native fullscreen is supported', async () => {
    await expect(browser.getFullscreen()).rejects.toMatchObject({ code: 'UNIMPLEMENTED' });
  });
});
