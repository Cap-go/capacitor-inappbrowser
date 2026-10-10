import { afterEach, beforeEach, describe, expect, it, mock } from 'bun:test';

import {
  InAppBrowserWeb,
  buildIsolatedDocument,
  createTrustedHtml,
  injectNativeBridge,
  mayAdoptPostMessageOrigin,
  resetTrustedHtmlPolicyCache,
  TRUSTED_TYPES_POLICY_BLOCKED_MESSAGE,
} from './web';

describe('openWebView on web', () => {
  let browser: InAppBrowserWeb;
  let mockWindow: {
    closed: boolean;
    location: { href: string };
    document: { open: () => void; write: (html: string) => void; close: () => void };
    close: () => void;
    postMessage: ReturnType<typeof mock>;
  };
  let openedWindows: (typeof mockWindow)[];
  const realSetInterval = globalThis.setInterval;
  const realClearInterval = globalThis.clearInterval;
  const realOpen = globalThis.open;
  const realFetch = globalThis.fetch;
  const realCreateObjectURL = URL.createObjectURL;
  const realRevokeObjectURL = URL.revokeObjectURL;

  beforeEach(() => {
    // No real 500 ms polling timers leak out of these tests.
    globalThis.setInterval = mock(() => 1) as unknown as typeof setInterval;
    globalThis.clearInterval = mock(() => undefined) as unknown as typeof clearInterval;
    const openMockHolder: { fn?: typeof window.open } = {};
    openedWindows = [];
    mockWindow = {
      closed: false,
      location: { href: '' },
      document: {
        open: () => undefined,
        write: () => undefined,
        close: () => undefined,
      },
      close: () => {
        mockWindow.closed = true;
      },
      postMessage: mock(() => undefined),
    };
    openMockHolder.fn = mock(() => {
      const win = {
        closed: false,
        location: { href: '' },
        document: {
          open: () => undefined,
          write: () => undefined,
          close: () => undefined,
        },
        close: () => {
          win.closed = true;
        },
        postMessage: mock(() => undefined),
      };
      openedWindows.push(win);
      mockWindow = win;
      return win as unknown as Window;
    }) as typeof window.open;
    (globalThis as { window?: Window }).window = {
      addEventListener: () => undefined,
      location: { href: 'https://app.test/' },
      open: openMockHolder.fn,
    } as Window;
    globalThis.open = openMockHolder.fn;
    browser = new InAppBrowserWeb();
  });

  afterEach(() => {
    for (const win of openedWindows) {
      win.close();
    }
    delete (globalThis as { window?: Window }).window;
    globalThis.setInterval = realSetInterval;
    globalThis.clearInterval = realClearInterval;
    globalThis.open = realOpen;
    globalThis.fetch = realFetch;
    URL.createObjectURL = realCreateObjectURL;
    URL.revokeObjectURL = realRevokeObjectURL;
  });

  const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

  it('opens a blank window synchronously then navigates', async () => {
    await expect(browser.openWebView({ url: 'https://example.com/page' })).resolves.toEqual({ id: 'web-1' });
    expect(globalThis.open).toHaveBeenCalledWith('', '_blank', '');
    expect(mockWindow.location.href).toBe('https://example.com/page');
  });

  it('closes the newest open tracked window when close() has no id', async () => {
    await browser.openWebView({ url: 'https://example.com/a' });
    await browser.openWebView({ url: 'https://example.com/b' });
    await browser.close();
    expect(mockWindow.closed).toBe(true);
  });

  it('forwards postMessage to the tracked window', async () => {
    await browser.openWebView({ url: 'https://example.com' });
    await browser.postMessage({ detail: { hello: 'world' } });
    expect(mockWindow.postMessage).toHaveBeenCalledWith({ hello: 'world' }, 'https://example.com');
  });

  it('passes popup window features when web.popup is set', async () => {
    await browser.openWebView({ url: 'https://example.com', web: { popup: true, width: 390, height: 844 } });
    expect(globalThis.open).toHaveBeenCalledWith('', '_blank', 'popup=yes,width=390,height=844');
  });

  it('loads header requests into an isolated sandboxed document and revokes the object URL on close', async () => {
    const fetchMock = mock(async () => ({
      ok: true,
      status: 200,
      url: 'https://example.com/final/page?x=1',
      text: async () => '<html><HEAD lang="en"><title>t</title></HEAD><body>hi</body></html>',
    }));
    globalThis.fetch = fetchMock as unknown as typeof fetch;
    let blobText = '';
    URL.createObjectURL = mock((blob: Blob) => {
      void blob.text().then((t) => (blobText = t));
      return 'blob:https://app.test/1';
    }) as typeof URL.createObjectURL;
    URL.revokeObjectURL = mock(() => undefined) as typeof URL.revokeObjectURL;

    await browser.openWebView({ url: 'https://example.com/start', headers: { Authorization: 'Bearer t' } });
    await flush();
    await flush();

    expect(fetchMock).toHaveBeenCalledWith('https://example.com/start', { headers: { Authorization: 'Bearer t' } });
    expect(mockWindow.location.href).toBe('blob:https://app.test/1');
    expect(blobText).toContain('sandbox="allow-scripts');
    expect(blobText).not.toContain('allow-same-origin');
    expect(blobText).toContain(
      '&lt;HEAD lang=&quot;en&quot;&gt;&lt;base href=&quot;https://example.com/final/page?x=1&quot;&gt;',
    );

    await browser.close();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:https://app.test/1');
  });

  it('closes the window when the header request fails', async () => {
    globalThis.fetch = mock(async () => ({
      ok: false,
      status: 401,
      url: '',
      text: async () => '',
    })) as unknown as typeof fetch;
    const errorSpy = console.error;
    console.error = () => undefined;
    try {
      await browser.openWebView({ url: 'https://example.com', headers: { 'X-Test': '1' } });
      await flush();
      await flush();
    } finally {
      console.error = errorSpy;
    }
    expect(mockWindow.closed).toBe(true);
  });

  it('targets the origin of the last message received after a same-site redirect', async () => {
    await browser.openWebView({ url: 'https://app.example.com' });
    const handler = (browser as unknown as { handleWindowMessage: (event: MessageEvent) => void }).handleWindowMessage;
    handler({
      source: mockWindow,
      origin: 'https://login.app.example.com',
      data: { ready: true },
    } as unknown as MessageEvent);
    await browser.postMessage({ detail: { hello: 'again' } });
    expect(mockWindow.postMessage).toHaveBeenCalledWith({ hello: 'again' }, 'https://login.app.example.com');
  });

  it('passes TrustedHTML from a named policy into DOMParser when trustedTypes is available', () => {
    const realDOMParser = globalThis.DOMParser;
    const trustedHtmlSentinel = { capgoTrustedHtml: true };
    const createHTML = mock(() => trustedHtmlSentinel);
    const createPolicy = mock(() => ({ createHTML }));
    const parseFromString = mock(() => {
      const body = {
        appendChild: () => undefined,
      };
      return {
        body,
        documentElement: { outerHTML: '<html><head></head><body></body></html>' },
        doctype: null,
        createElement: () => ({ textContent: '' }),
      } as Document;
    });
    resetTrustedHtmlPolicyCache();
    (globalThis as { window?: Window }).window = {
      trustedTypes: { createPolicy },
    } as Window;
    globalThis.DOMParser = class {
      parseFromString = parseFromString;
    } as unknown as typeof DOMParser;
    try {
      injectNativeBridge('<html><body></body></html>');
      expect(createPolicy).toHaveBeenCalledWith('capgo-inappbrowser', { createHTML: expect.any(Function) });
      expect(createHTML).toHaveBeenCalled();
      expect(parseFromString).toHaveBeenCalledWith(trustedHtmlSentinel, 'text/html');
    } finally {
      globalThis.DOMParser = realDOMParser;
      resetTrustedHtmlPolicyCache();
      delete (globalThis as { window?: Window }).window;
    }
  });

  it('runs createTrustedHtml for sandbox srcdoc when building an isolated document', () => {
    const realDOMParser = globalThis.DOMParser;
    const parseTrustedSentinel = { capgoTrustedHtml: 'parse' };
    const srcdocTrustedSentinel = {
      toString: () => 'capgo-srcdoc-trusted-marker',
    };
    const createHTML = mock()
      .mockImplementationOnce(() => parseTrustedSentinel)
      .mockImplementationOnce(() => srcdocTrustedSentinel);
    const createPolicy = mock(() => ({ createHTML }));
    const parseFromString = mock(() => {
      const body = { appendChild: () => undefined };
      return {
        body,
        documentElement: { outerHTML: '<html><body></body></html>' },
        doctype: null,
        createElement: () => ({ textContent: '' }),
      } as Document;
    });
    resetTrustedHtmlPolicyCache();
    (globalThis as { window?: Window }).window = {
      trustedTypes: { createPolicy },
    } as Window;
    globalThis.DOMParser = class {
      parseFromString = parseFromString;
    } as unknown as typeof DOMParser;
    try {
      const wrapper = buildIsolatedDocument('<html><body>hi</body></html>', 'https://example.com/');
      expect(parseFromString).toHaveBeenCalledWith(parseTrustedSentinel, 'text/html');
      expect(createHTML).toHaveBeenCalledTimes(2);
      const srcdocMatch = wrapper.match(/srcdoc="([^"]*)"/);
      expect(srcdocMatch).not.toBeNull();
      expect(srcdocMatch![1]).toContain('capgo-srcdoc-trusted-marker');
    } finally {
      globalThis.DOMParser = realDOMParser;
      resetTrustedHtmlPolicyCache();
      delete (globalThis as { window?: Window }).window;
    }
  });

  it('throws when trustedTypes is present but createPolicy is blocked', () => {
    resetTrustedHtmlPolicyCache();
    (globalThis as { window?: Window }).window = {
      trustedTypes: {
        createPolicy: () => {
          throw new Error('CSP blocked');
        },
      },
    } as Window;
    try {
      expect(() => createTrustedHtml('<html><body></body></html>')).toThrow(TRUSTED_TYPES_POLICY_BLOCKED_MESSAGE);
    } finally {
      resetTrustedHtmlPolicyCache();
      delete (globalThis as { window?: Window }).window;
    }
  });

  it('preserves doctype when injecting the native bridge', () => {
    const html = '<!DOCTYPE html><html><head></head><body><p>x</p></body></html>';
    expect(injectNativeBridge(html).startsWith('<!DOCTYPE html>')).toBe(true);
  });

  it('rejects parent hostnames for postMessage target adoption', () => {
    expect(mayAdoptPostMessageOrigin('https://login.app.example.com', 'https://app.example.com')).toBe(false);
  });

  it('rejects scheme downgrades for postMessage target adoption', () => {
    expect(mayAdoptPostMessageOrigin('https://app.example.com', 'http://login.app.example.com')).toBe(false);
  });

  it('rejects a different port for postMessage target adoption', () => {
    expect(mayAdoptPostMessageOrigin('https://app.example.com', 'https://login.app.example.com:8443')).toBe(false);
    expect(mayAdoptPostMessageOrigin('https://app.example.com:8443', 'https://login.app.example.com:8443')).toBe(true);
  });

  it('ignores message origins outside the opened site', async () => {
    await browser.openWebView({ url: 'https://app.example.com' });
    const handler = (browser as unknown as { handleWindowMessage: (event: MessageEvent) => void }).handleWindowMessage;
    handler({
      source: mockWindow,
      origin: 'https://evil.example.org',
      data: { forged: true },
    } as unknown as MessageEvent);
    await browser.postMessage({ detail: { hello: 'safe' } });
    expect(mockWindow.postMessage).toHaveBeenCalledWith({ hello: 'safe' }, 'https://app.example.com');
  });
});
