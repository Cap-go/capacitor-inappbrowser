package ee.forgr.capacitor_inappbrowser;

import java.net.URL;

final class ProxyRequestSupport {

    private ProxyRequestSupport() {}

    static boolean shouldInjectBridge(Options options) {
        return options != null && options.shouldEnableNativeProxy();
    }

    static boolean supportsNativeHttpRequest(URL url) {
        if (url == null) {
            return false;
        }
        String protocol = url.getProtocol();
        return "http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol);
    }
}
