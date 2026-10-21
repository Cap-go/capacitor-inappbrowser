import { buildConsoleCaptureScript } from './web-console-capture';

export type ConsoleCaptureWindow = Window & { __capgoInAppBrowserConsoleCaptureInstalled?: boolean };

export type ConsoleCaptureHost = {
  window: Window;
  consoleCaptureInstalledDocument?: Document;
  consoleCaptureTimer?: number;
};

export function childDocumentHref(child: Window): string | undefined {
  try {
    const href = child.location.href;
    if (!href || href === 'about:blank') {
      return undefined;
    }
    return href;
  } catch {
    return undefined;
  }
}

export function isConsoleCaptureActive(child: ConsoleCaptureWindow): boolean {
  try {
    return child.__capgoInAppBrowserConsoleCaptureInstalled === true;
  } catch {
    return false;
  }
}

export function installConsoleCaptureScript(child: Window): void {
  if (child.closed || !childDocumentHref(child)) {
    return;
  }
  try {
    const script = buildConsoleCaptureScript();
    const doc = child.document;
    if (!doc) {
      return;
    }
    const target = doc.head ?? doc.documentElement;
    if (!target) {
      return;
    }
    const element = doc.createElement('script');
    element.text = script;
    target.appendChild(element);
    element.remove();
  } catch {
    return;
  }
}

export function ensureConsoleCaptureInstalled(host: ConsoleCaptureHost): void {
  if (!childDocumentHref(host.window)) {
    return;
  }
  const child = host.window as ConsoleCaptureWindow;
  const doc = child.document;
  if (isConsoleCaptureActive(child) && host.consoleCaptureInstalledDocument === doc) {
    return;
  }
  installConsoleCaptureScript(host.window);
  if (isConsoleCaptureActive(child)) {
    host.consoleCaptureInstalledDocument = doc;
  }
}
