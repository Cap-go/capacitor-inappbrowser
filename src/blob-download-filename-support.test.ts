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

test('resolveBlobDownloadFileName prefers in-document attribute over stale recording', () => {
  const blobUrl = 'blob:https://example.com/stale';
  const fileName = resolveBlobDownloadFileName(
    blobUrl,
    { [blobUrl]: 'placeholder.txt' },
    [{ href: blobUrl, download: 'final-report.txt' }],
    'downloadfile.txt',
  );
  expect(fileName).toBe('final-report.txt');
});

test('resolveBlobDownloadFileName uses fallback when in-document download is empty', () => {
  const blobUrl = 'blob:https://example.com/cleared';
  const fileName = resolveBlobDownloadFileName(
    blobUrl,
    { [blobUrl]: 'first.txt' },
    [{ href: blobUrl, download: '' }],
    'downloadfile.txt',
  );
  expect(fileName).toBe('downloadfile.txt');
});
