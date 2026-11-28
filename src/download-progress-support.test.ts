import { describe, expect, it } from 'bun:test';

import {
  DOWNLOAD_PROGRESS_MIN_INTERVAL_MS,
  ManagedDownloadEndingGuard,
  shouldEmitDownloadProgress,
} from './download-progress-support';

describe('shouldEmitDownloadProgress', () => {
  it('allows the first progress sample immediately', () => {
    expect(shouldEmitDownloadProgress(0, 1000)).toBe(true);
  });

  it('throttles to at most four events per second', () => {
    const t0 = 1000;
    expect(shouldEmitDownloadProgress(t0, t0 + DOWNLOAD_PROGRESS_MIN_INTERVAL_MS - 1)).toBe(false);
    expect(shouldEmitDownloadProgress(t0, t0 + DOWNLOAD_PROGRESS_MIN_INTERVAL_MS)).toBe(true);
  });
});

describe('ManagedDownloadEndingGuard', () => {
  it('allows exactly one ending after start', () => {
    const guard = new ManagedDownloadEndingGuard();
    expect(guard.markStarted()).toBe(true);
    expect(guard.trySetEnding('completed')).toBe(true);
    expect(guard.trySetEnding('failed')).toBe(false);
    expect(guard.trySetEnding('cancelled')).toBe(false);
    expect(guard.getEnding()).toBe('completed');
  });

  it('rejects endings before start', () => {
    const guard = new ManagedDownloadEndingGuard();
    expect(guard.trySetEnding('failed')).toBe(false);
  });
});
