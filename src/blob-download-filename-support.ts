export type BlobDownloadLink = {
  href: string;
  download: string | null;
};

/**
 * Mirrors the blob download filename resolution used in native WebView scripts.
 */
export function resolveBlobDownloadFileName(
  blobUrl: string,
  recordedNames: Record<string, string> | undefined,
  documentLinks: BlobDownloadLink[],
  fallbackFileName: string,
): string {
  const recorded = recordedNames?.[blobUrl];
  if (recorded) {
    return recorded;
  }
  const matchingLink = documentLinks.find((link) => link.href === blobUrl);
  const fromAttribute = matchingLink?.download;
  if (fromAttribute) {
    return fromAttribute;
  }
  return fallbackFileName;
}