export type BundledAssetPlatform = 'ios' | 'android';

export interface BundledAssetLocalConfig {
  scheme: string;
  host: string;
}

export interface BundledAssetResolution {
  url: string;
}

export const BUNDLED_ASSET_DEFAULTS: Record<BundledAssetPlatform, BundledAssetLocalConfig> = {
  ios: { scheme: 'capacitor', host: 'localhost' },
  android: { scheme: 'https', host: 'localhost' },
};

const ABSOLUTE_URL_PATTERN = /^[a-zA-Z][a-zA-Z\d+\-.]*:/;
const RESERVED_WEB_SCHEMES = ['http', 'https'];

export function isAbsoluteUrl(url: string): boolean {
  return ABSOLUTE_URL_PATTERN.test(url) || url.startsWith('//');
}

export function containsPathTraversal(path: string): boolean {
  return path.split('/').some((segment) => segment === '..');
}

export function normalizeBundledPath(path: string): string | null {
  const trimmed = path.trim();
  if (!trimmed || trimmed === '/') {
    return '/';
  }

  if (containsPathTraversal(trimmed)) {
    return null;
  }

  return trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
}

export function parseBundledLocalConfig(localUrl?: string | null): BundledAssetLocalConfig | null {
  if (!localUrl) {
    return null;
  }

  try {
    const parsed = new URL(localUrl.endsWith('/') ? localUrl : `${localUrl}/`);
    if (!parsed.protocol || !parsed.hostname) {
      return null;
    }

    return {
      scheme: parsed.protocol.replace(/:$/, '').toLowerCase(),
      host: parsed.hostname.toLowerCase(),
    };
  } catch {
    return null;
  }
}

export function handlerSchemeForPlatform(platform: BundledAssetPlatform, localConfig: BundledAssetLocalConfig): string {
  if (platform === 'ios' && RESERVED_WEB_SCHEMES.includes(localConfig.scheme)) {
    return BUNDLED_ASSET_DEFAULTS.ios.scheme;
  }

  if (platform === 'android') {
    if (localConfig.scheme === 'http' || localConfig.scheme === 'https') {
      return localConfig.scheme;
    }
    return BUNDLED_ASSET_DEFAULTS.android.scheme;
  }

  return localConfig.scheme;
}

export function isBundledLocalUrl(
  url: string,
  platform: BundledAssetPlatform,
  localConfig?: BundledAssetLocalConfig | null,
): boolean {
  if (!isAbsoluteUrl(url)) {
    return false;
  }

  try {
    const parsed = new URL(url);
    const scheme = parsed.protocol.replace(/:$/, '').toLowerCase();
    const host = parsed.hostname.toLowerCase();
    const config = localConfig ?? BUNDLED_ASSET_DEFAULTS[platform];

    if (host !== config.host) {
      return false;
    }

    if (parsed.port !== '') {
      return false;
    }

    if (scheme === config.scheme || scheme === handlerSchemeForPlatform(platform, config)) {
      return true;
    }

    return RESERVED_WEB_SCHEMES.includes(scheme) && RESERVED_WEB_SCHEMES.includes(config.scheme);
  } catch {
    return false;
  }
}

export function isRelativeBundledPath(url: string): boolean {
  const trimmed = url.trim();
  if (!trimmed) {
    return false;
  }

  return !isAbsoluteUrl(trimmed);
}

function rewriteBundledLocalUrl(
  url: string,
  localConfig: BundledAssetLocalConfig,
  navigationScheme: string,
): string | null {
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.toLowerCase();
    const scheme = parsed.protocol.replace(/:$/, '').toLowerCase();

    if (host !== localConfig.host || scheme === navigationScheme) {
      return null;
    }

    const pathname = parsed.pathname || '/';
    const search = parsed.search;
    const hash = parsed.hash;
    return `${navigationScheme}://${host}${pathname}${search}${hash}`;
  } catch {
    return null;
  }
}

export function resolveBundledAssetUrl(
  url: string,
  platform: BundledAssetPlatform,
  localConfig?: BundledAssetLocalConfig | null,
): BundledAssetResolution | null {
  const trimmed = url.trim();
  const config = localConfig ?? BUNDLED_ASSET_DEFAULTS[platform];
  const navigationScheme = handlerSchemeForPlatform(platform, config);

  if (isRelativeBundledPath(trimmed)) {
    const path = normalizeBundledPath(trimmed);
    if (path === null) {
      return null;
    }
    return {
      url: `${navigationScheme}://${config.host}${path}`,
    };
  }

  if (isBundledLocalUrl(trimmed, platform, config)) {
    const rewritten = rewriteBundledLocalUrl(trimmed, config, navigationScheme);
    return {
      url: rewritten ?? trimmed,
    };
  }

  return {
    url: trimmed,
  };
}
