import { expect, test } from 'bun:test';
import { resolveBlobDownloadFileName } from './blob-download-filename-support';

test('resolveBlobDownloadFileName prefers recorded detached link name', () => {
  const blobUrl = 'blob:https://example.com/abc-123';
  const fileName = resolveBlobDownloadFileName(blobUrl, { [blobUrl]: 'report-2026.pdf' }, [], 'downloadfile.pdf');
  expect(fileName).toBe('report-2026.pdf');
});

test('resolveBlobDownloadFileName uses in-document link when no recording', () => {
  const blobUrl = 'blob:https://example.com/def-456';
  const fileName = resolveBlobDownloadFileName(
    blobUrl,
    undefined,
    [{ href: blobUrl, download: 'capgo-download-demo.txt' }],
    'downloadfile.txt',
  );
  expect(fileName).toBe('capgo-download-demo.txt');
});

test('resolveBlobDownloadFileName falls back when link is missing', () => {
  const fileName = resolveBlobDownloadFileName('blob:https://example.com/missing', undefined, [], 'downloadfile.pdf');
  expect(fileName).toBe('downloadfile.pdf');
});
