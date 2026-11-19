const INTERNAL_SCHEMES = new Set(['about', 'data', 'blob', 'javascript']);
const APP_STORE_HOSTS = new Set(['itunes.apple.com', 'apps.apple.com']);

const normalizeHost = (host: string | null | undefined): string | null => {
  if (!host) {
    return null;
  }
  const lower = host.toLowerCase();
  return lower.startsWith('www.') ? lower.slice(4) : lower;
};

export const isAuthorizedAppLink = (url: string, authorizedAppLinks: string[]): boolean => {
  if (!authorizedAppLinks.length) {
    return false;
  }

  let urlHost: string | null;
  try {
    urlHost = normalizeHost(new URL(url).hostname);
  } catch {
    return false;
  }
  if (!urlHost) {
    return false;
  }

  for (const authorized of authorizedAppLinks) {
    try {
      const authHost = normalizeHost(new URL(authorized).hostname);
      if (authHost && authHost === urlHost) {
        return true;
      }
    } catch {
      // skip invalid configured entries
    }
  }

  return false;
};

/** Mirrors native logic for unit tests; web does not emit `deepLinkPrevented` today. */
export const shouldEmitDeepLinkPreventedEvent = (rawUrl: string, authorizedAppLinks: string[] = []): boolean => {
  if (!rawUrl.trim()) {
    return false;
  }

  let url: URL;
  try {
    url = new URL(rawUrl);
  } catch {
    return false;
  }

  const scheme = url.protocol.replace(/:$/, '').toLowerCase();
  if (!scheme) {
    return false;
  }
  if (INTERNAL_SCHEMES.has(scheme)) {
    return false;
  }

  if (scheme !== 'http' && scheme !== 'https' && scheme !== 'file') {
    return true;
  }

  if (isAuthorizedAppLink(rawUrl, authorizedAppLinks)) {
    return true;
  }

  const host = normalizeHost(url.hostname);
  return host != null && APP_STORE_HOSTS.has(host);
};
