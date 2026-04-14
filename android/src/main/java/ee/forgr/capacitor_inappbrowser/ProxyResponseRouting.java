package ee.forgr.capacitor_inappbrowser;

import java.util.Map;

final class ProxyResponseRouting {

    interface ProxyRequestLocator {
        boolean hasPendingProxyRequest(String requestId);
    }

    private ProxyResponseRouting() {}

    static <T extends ProxyRequestLocator> T resolveTargetDialog(String webviewId, String requestId, Map<String, T> dialogs) {
        if (requestId == null || requestId.isBlank() || dialogs == null || dialogs.isEmpty()) {
            return null;
        }

        if (webviewId != null && !webviewId.isBlank()) {
            return dialogs.get(webviewId);
        }

        T matchedDialog = null;
        for (T dialog : dialogs.values()) {
            if (dialog == null || !dialog.hasPendingProxyRequest(requestId)) {
                continue;
            }
            if (matchedDialog != null && matchedDialog != dialog) {
                return null;
            }
            matchedDialog = dialog;
        }

        return matchedDialog;
    }
}
