export type BundledAssetPlatform = 'ios' | 'android';

export interface BundledAssetLocalConfig {
  scheme: string;
  host: string;
}

export interface BundledAssetResolution {
  url: string;
  needsHandler: boolean;
}

export const BUNDLED_ASSET_DEFAULTS: Record<BundledAssetPlatform, BundledAssetLocalConfig> = {
  ios: { scheme: 'capacitor', host: 'localhost' },
  android: { scheme: 'https', host: 'localhost' },
};

const ABSOLUTE_URL_PATTERN = /^[a-zA-Z][a-zA-Z\d+\-.]*:/;

export function isAbsoluteUrl(url: string): boolean {
  return ABSOLUTE_URL_PATTERN.test(url) || url.startsWith('//');
}

export function normalizeBundledPath(path: string): string {
  const trimmed = path.trim();
  if (!trimmed || trimmed === '/') {
    return '/';
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

export function isBundledLocalUrl(url: string, localConfig?: BundledAssetLocalConfig | null): boolean {
  if (!isAbsoluteUrl(url)) {
    return false;
  }

  try {
    const parsed = new URL(url);
    const scheme = parsed.protocol.replace(/:$/, '').toLowerCase();
    const host = parsed.hostname.toLowerCase();
    const config = localConfig ?? BUNDLED_ASSET_DEFAULTS.android;

    return scheme === config.scheme && host === config.host;
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

export function resolveBundledAssetUrl(
  url: string,
  platform: BundledAssetPlatform,
  localConfig?: BundledAssetLocalConfig | null,
): BundledAssetResolution {
  const trimmed = url.trim();
  const defaults = localConfig ?? BUNDLED_ASSET_DEFAULTS[platform];

  if (isRelativeBundledPath(trimmed)) {
    const path = normalizeBundledPath(trimmed);
    return {
      url: `${defaults.scheme}://${defaults.host}${path}`,
      needsHandler: true,
    };
  }

  if (isBundledLocalUrl(trimmed, defaults)) {
    return {
      url: trimmed,
      needsHandler: true,
    };
  }

  return {
    url: trimmed,
    needsHandler: false,
  };
}
