import { InAppBrowser, ToolBarType, addProxyHandler } from '@capgo/capacitor-inappbrowser';
import page from '../fullscreen-demo.html?raw';

const demoUrl = 'https://fullscreen.capgo.test/demo';

export function setupFullscreenDemo(root) {
  const openButton = root.querySelector('#fullscreen-open');
  const showButton = root.querySelector('#fullscreen-show');
  const status = root.querySelector('#fullscreen-status');
  let activeId;
  let handles = [];
  let showTimer;

  async function cleanup() {
    clearTimeout(showTimer);
    activeId = undefined;
    const ownedHandles = handles;
    handles = [];
    await Promise.all(ownedHandles.map((handle) => handle.remove()));
    openButton.disabled = false;
    showButton.disabled = true;
  }

  async function sendState(id) {
    const { enabled } = await InAppBrowser.getFullscreen({ id });
    await InAppBrowser.postMessage({ id, detail: { type: 'fullscreenDemo', enabled } });
    status.textContent = `Fullscreen: ${enabled}`;
  }

  async function show(id) {
    try {
      await InAppBrowser.show({ id });
      await sendState(id);
    } catch (error) {
      status.textContent = String(error);
    }
  }

  showButton.addEventListener('click', () => show(activeId));

  openButton.addEventListener('click', async () => {
    openButton.disabled = true;
    try {
      handles.push(
        await addProxyHandler(async (request) => {
          if (request.url.split('#')[0] !== demoUrl) return null;
          return new Response(page, { headers: { 'Content-Type': 'text/html', 'Cache-Control': 'no-store' } });
        }),
      );
      handles.push(
        await InAppBrowser.addListener('fullscreenChange', ({ id, enabled }) => {
          if (activeId && id !== activeId) return;
          status.textContent = `Fullscreen: ${enabled}`;
          void InAppBrowser.postMessage({ id, detail: { type: 'fullscreenDemo', enabled } }).catch(console.error);
        }),
      );
      handles.push(
        await InAppBrowser.addListener('closeEvent', ({ id }) => {
          if (id === activeId) void cleanup().catch(console.error);
        }),
      );
      handles.push(
        await InAppBrowser.addListener('messageFromWebview', async ({ id, detail }) => {
          if (!id || detail?.type !== 'fullscreenDemo' || (activeId && id !== activeId)) return;
          try {
            if (detail.action === 'enter' || detail.action === 'exit') {
              await InAppBrowser.setFullscreen({ id, enabled: detail.action === 'enter' });
            } else if (detail.action === 'hide') {
              await InAppBrowser.hide({ id });
              clearTimeout(showTimer);
              showTimer = setTimeout(() => {
                if (id === activeId) void show(id);
              }, 2000);
            }
            await sendState(id);
          } catch (error) {
            status.textContent = String(error);
            await InAppBrowser.postMessage({ id, detail: { type: 'fullscreenDemo', error: String(error) } }).catch(
              console.error,
            );
          }
        }),
      );
      const { id } = await InAppBrowser.openWebView({
        url: demoUrl,
        proxyRequests: true,
        toolbarType: ToolBarType.NAVIGATION,
        enabledSafeTopMargin: true,
        enabledSafeBottomMargin: true,
        activeNativeNavigationForWebview: true,
        fullscreen: root.querySelector('#fullscreen-start').checked,
        hidden: root.querySelector('#fullscreen-hidden').checked,
        isPresentAfterPageLoad: root.querySelector('#fullscreen-deferred').checked,
      });
      activeId = id;
      showButton.disabled = false;
      await sendState(id);
    } catch (error) {
      status.textContent = String(error);
      if (activeId) await InAppBrowser.close({ id: activeId }).catch(console.error);
      await cleanup();
    }
  });
}
