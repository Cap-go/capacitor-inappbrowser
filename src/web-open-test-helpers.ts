import { mock } from 'bun:test';

import type { InAppBrowserWeb } from './web';
import { CAPGO_CONSOLE_POST_TYPE } from './web-console-capture';

export type MockPopoutWindow = {
  closed: boolean;
  location: { href: string; origin?: string };
  document: {
    open: () => void;
    write: (html: string) => void;
    close: () => void;
    head: { appendChild: ReturnType<typeof mock> };
    createElement: ReturnType<typeof mock>;
  };
  close: () => void;
  postMessage: ReturnType<typeof mock>;
  __capgoInAppBrowserConsoleCaptureInstalled?: boolean;
};

export function createMockPopoutWindow(
  createScriptElement: () => { text: string; remove: ReturnType<typeof mock> },
  markConsoleCaptureInstalled: (win: MockPopoutWindow, element: { text?: string }) => void,
): MockPopoutWindow {
  const win: MockPopoutWindow = {
    closed: false,
    location: { href: 'https://example.com/page', origin: 'https://example.com' },
    document: {
      open: () => undefined,
      write: () => undefined,
      close: () => undefined,
      head: {
        appendChild: mock((element: { text?: string }) => {
          markConsoleCaptureInstalled(win, element);
        }),
      },
      createElement: mock(() => createScriptElement()),
    },
    close: () => {
      win.closed = true;
    },
    postMessage: mock(() => undefined),
  };
  return win;
}

export function getWindowMessageHandler(browser: InAppBrowserWeb): (event: MessageEvent) => void {
  return (browser as unknown as { handleWindowMessage: (event: MessageEvent) => void }).handleWindowMessage;
}

export function dispatchConsolePostMessage(
  browser: InAppBrowserWeb,
  source: MockPopoutWindow,
  options: { origin: string; message: string; level?: string },
): void {
  getWindowMessageHandler(browser)({
    source,
    origin: options.origin,
    data: {
      type: CAPGO_CONSOLE_POST_TYPE,
      level: options.level ?? 'log',
      message: options.message,
    },
  } as unknown as MessageEvent);
}
