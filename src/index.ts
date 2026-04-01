import { registerPlugin } from '@capacitor/core';

import type { InAppBrowserPlugin } from './definitions';

export const InAppBrowser = registerPlugin<InAppBrowserPlugin>('InAppBrowser', {
  web: () => import('./web').then((m) => new m.InAppBrowserWeb()),
});

export * from './definitions';
