package ee.forgr.capacitor_inappbrowser;

import java.util.concurrent.atomic.AtomicInteger;

public final class ManagedDownloadSupport {

    public static final long PROGRESS_MIN_INTERVAL_MS = 250L;

    public enum EndingKind {
        NONE,
        STARTED,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private ManagedDownloadSupport() {}

    public static boolean shouldEmitProgress(long lastEmitMs, long nowMs) {
        return lastEmitMs <= 0L || nowMs - lastEmitMs >= PROGRESS_MIN_INTERVAL_MS;
    }

    /** Normalizes a reported content length: any negative value means the size is unknown (-1). */
    public static long resolveBytesExpected(long contentLength) {
        return contentLength >= 0L ? contentLength : -1L;
    }

    /** True when the server announced a length and the stream ended before delivering all of it. */
    public static boolean isTruncated(long bytesReceived, long bytesExpected) {
        return bytesExpected >= 0L && bytesReceived < bytesExpected;
    }

    public static final class EndingGuard {

        private final AtomicInteger state = new AtomicInteger(EndingKind.NONE.ordinal());

        public boolean markStarted() {
            return state.compareAndSet(EndingKind.NONE.ordinal(), EndingKind.STARTED.ordinal());
        }

        public boolean tryComplete() {
            return state.compareAndSet(EndingKind.STARTED.ordinal(), EndingKind.COMPLETED.ordinal());
        }

        public boolean tryFail() {
            return state.compareAndSet(EndingKind.STARTED.ordinal(), EndingKind.FAILED.ordinal());
        }

        public boolean tryCancel() {
            return state.compareAndSet(EndingKind.STARTED.ordinal(), EndingKind.CANCELLED.ordinal());
        }

        public boolean hasEnded() {
            int value = state.get();
            return (
                value == EndingKind.COMPLETED.ordinal() || value == EndingKind.FAILED.ordinal() || value == EndingKind.CANCELLED.ordinal()
            );
        }
    }
}
