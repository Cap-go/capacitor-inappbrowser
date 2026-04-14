package ee.forgr.capacitor_inappbrowser;

final class ActiveWebViewSupport {

    private ActiveWebViewSupport() {}

    static boolean shouldActivateNewWebView(boolean isHidden, boolean hasActiveWebView) {
        return !isHidden || !hasActiveWebView;
    }
}
