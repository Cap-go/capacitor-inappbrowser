function loopbackHost() {
  const configuredHost = globalThis.localStorage?.getItem("capgo.inappbrowser.host");
  if (configuredHost) {
    return configuredHost;
  }
  return window.location.hostname || "localhost";
}

export function getLoopbackBaseUrl(port) {
  return `http://${loopbackHost()}:${port}`;
}

export const url = `${getLoopbackBaseUrl(8000)}/index.php`;
