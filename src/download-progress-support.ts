/** Maximum download progress events per second (throttle interval 250 ms). */
export const DOWNLOAD_PROGRESS_MIN_INTERVAL_MS = 250;

export type ManagedDownloadEnding = 'completed' | 'failed' | 'cancelled';

export function shouldEmitDownloadProgress(lastEmitMs: number, nowMs: number): boolean {
  return lastEmitMs <= 0 || nowMs - lastEmitMs >= DOWNLOAD_PROGRESS_MIN_INTERVAL_MS;
}

export class ManagedDownloadEndingGuard {
  private started = false;
  private ending: ManagedDownloadEnding | null = null;

  markStarted(): boolean {
    if (this.started) {
      return false;
    }
    this.started = true;
    return true;
  }

  trySetEnding(kind: ManagedDownloadEnding): boolean {
    if (!this.started || this.ending !== null) {
      return false;
    }
    this.ending = kind;
    return true;
  }

  hasStarted(): boolean {
    return this.started;
  }

  hasEnded(): boolean {
    return this.ending !== null;
  }

  getEnding(): ManagedDownloadEnding | null {
    return this.ending;
  }
}
