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
  const matchingLink = documentLinks.find((link) => link.href === blobUrl);
  if (matchingLink && matchingLink.download !== null) {
    if (matchingLink.download) {
      return matchingLink.download;
    }
    return fallbackFileName;
  }
  const recorded = recordedNames?.[blobUrl];
  if (recorded) {
    return recorded;
  }
  return fallbackFileName;
}
