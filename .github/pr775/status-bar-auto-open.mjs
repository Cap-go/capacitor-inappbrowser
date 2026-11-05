import { InAppBrowser, ToolBarType, BackgroundColor } from '@capgo/capacitor-inappbrowser';

const OPEN_DELAY_MS = 2500;

await new Promise((resolve) => {
  globalThis.setTimeout(resolve, OPEN_DELAY_MS);
});

await InAppBrowser.openWebView({
  url: 'https://example.com/',
  toolbarColor: '#FFFFFF',
  toolbarTextColor: '#000000',
  toolbarType: ToolBarType.NAVIGATION,
  backgroundColor: BackgroundColor.WHITE,
  title: 'PR 775 status bar',
  visibleTitle: true,
  showArrow: true,
  showReloadButton: false,
});
