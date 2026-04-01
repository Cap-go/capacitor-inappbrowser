function loopbackHost() {
  const configuredHost = globalThis.localStorage?.getItem("capgo.inappbrowser.host");
  if (configuredHost) {
    return configuredHost;
  }
  const capacitor = globalThis.Capacitor;
  if (capacitor && typeof capacitor.isNativePlatform === "function" && capacitor.isNativePlatform()) {
    return "127.0.0.1";
  }
  return window.location.hostname || "localhost";
}

export function getLoopbackBaseUrl(port) {
  return `http://${loopbackHost()}:${port}`;
}

export const url = `${getLoopbackBaseUrl(8000)}/index.php`;
