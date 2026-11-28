package ee.forgr.capacitor_inappbrowser;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks managed download lifecycle events for a single {@link WebViewDialog}.
 */
public final class ManagedDownloadCoordinator {

    public static final long PROGRESS_MIN_INTERVAL_MS = 250L;

    public interface Callbacks {
        void downloadStarted(String downloadId, String sourceUrl);

        void downloadProgress(String downloadId, long bytesReceived, long bytesExpected, String fileName);

        void downloadCancelled(String downloadId, String sourceUrl);
    }

    static final class ManagedDownload {

        final String downloadId;
        final String sourceUrl;
        final AtomicBoolean terminalEmitted = new AtomicBoolean(false);
        final AtomicBoolean cancelRequested = new AtomicBoolean(false);
        volatile long bytesReceived;
        volatile long bytesExpected = -1L;
        volatile String fileName;
        volatile long lastProgressEmitMs;
    }

    private final Callbacks callbacks;
    private final Map<String, ManagedDownload> downloads = new ConcurrentHashMap<>();

    public ManagedDownloadCoordinator(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public static boolean shouldEmitProgress(long lastEmitMs, long nowMs) {
        return lastEmitMs == 0L || nowMs - lastEmitMs >= PROGRESS_MIN_INTERVAL_MS;
    }

    public static long resolveBytesExpected(long contentLength) {
        return contentLength >= 0L ? contentLength : -1L;
    }

    public String begin(String sourceUrl) {
        String downloadId = UUID.randomUUID().toString();
        ManagedDownload download = new ManagedDownload();
        download.downloadId = downloadId;
        download.sourceUrl = sourceUrl;
        downloads.put(downloadId, download);
        callbacks.downloadStarted(downloadId, sourceUrl);
        return downloadId;
    }

    public void registerExisting(String downloadId, String sourceUrl) {
        ManagedDownload download = new ManagedDownload();
        download.downloadId = downloadId;
        download.sourceUrl = sourceUrl;
        downloads.put(downloadId, download);
        callbacks.downloadStarted(downloadId, sourceUrl);
    }

    public ManagedDownload get(String downloadId) {
        if (downloadId == null) {
            return null;
        }
        return downloads.get(downloadId);
    }

    public boolean isCancelRequested(String downloadId) {
        ManagedDownload download = get(downloadId);
        return download != null && download.cancelRequested.get();
    }

    public void setBytesExpected(String downloadId, long bytesExpected) {
        ManagedDownload download = get(downloadId);
        if (download != null) {
            download.bytesExpected = bytesExpected;
        }
    }

    public void setFileName(String downloadId, String fileName) {
        ManagedDownload download = get(downloadId);
        if (download != null) {
            download.fileName = fileName;
        }
    }

    public void reportBytesReceived(String downloadId, long bytesReceived) {
        ManagedDownload download = get(downloadId);
        if (download == null || download.terminalEmitted.get()) {
            return;
        }
        download.bytesReceived = bytesReceived;
        long nowMs = System.currentTimeMillis();
        if (!shouldEmitProgress(download.lastProgressEmitMs, nowMs)) {
            return;
        }
        download.lastProgressEmitMs = nowMs;
        callbacks.downloadProgress(downloadId, bytesReceived, download.bytesExpected, download.fileName);
    }

    public void emitFinalProgress(String downloadId) {
        ManagedDownload download = get(downloadId);
        if (download == null || download.terminalEmitted.get()) {
            return;
        }
        callbacks.downloadProgress(downloadId, download.bytesReceived, download.bytesExpected, download.fileName);
    }

    public boolean markTerminal(String downloadId) {
        ManagedDownload download = downloads.remove(downloadId);
        if (download == null) {
            return false;
        }
        return download.terminalEmitted.compareAndSet(false, true);
    }

    public boolean requestCancel(String downloadId) {
        ManagedDownload download = downloads.get(downloadId);
        if (download == null) {
            return false;
        }
        download.cancelRequested.set(true);
        if (download.terminalEmitted.get()) {
            return true;
        }
        if (!download.terminalEmitted.compareAndSet(false, true)) {
            return true;
        }
        downloads.remove(downloadId);
        callbacks.downloadCancelled(downloadId, download.sourceUrl);
        return true;
    }

    public void cancelAll() {
        for (String downloadId : downloads.keySet().toArray(new String[0])) {
            requestCancel(downloadId);
        }
    }
}
