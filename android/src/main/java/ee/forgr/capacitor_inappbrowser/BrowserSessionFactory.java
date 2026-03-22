package ee.forgr.capacitor_inappbrowser;

import android.app.Activity;
import android.content.Context;
import android.webkit.WebView;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public final class BrowserSessionFactory {

    private BrowserSessionFactory() {}

    public static BrowserSession create(InAppBrowserPlugin plugin, Options options) {
        if (options.getBrowserEngine() == Options.BrowserEngine.GECKO) {
            if (!DependencyAvailabilityChecker.isGeckoAvailable()) {
                throw new IllegalStateException(
                    "GeckoView engine was requested but the optional org.mozilla.geckoview dependency is not on the classpath"
                );
            }

            validateGeckoOptions(options);

            return createGeckoSession(
                plugin.getContext(),
                android.R.style.Theme_NoTitleBar,
                options,
                plugin,
                plugin.getBridge().getWebView(),
                plugin.getActivity()
            );
        }

        return new SystemWebViewSession(
            plugin.getContext(),
            android.R.style.Theme_NoTitleBar,
            options,
            plugin,
            plugin.getBridge().getWebView(),
            plugin.getActivity()
        );
    }

    private static BrowserSession createGeckoSession(
        Context context,
        int theme,
        Options options,
        WebViewDialog.PermissionHandler permissionHandler,
        WebView capacitorWebView,
        Activity activity
    ) {
        try {
            Class<?> geckoSessionClass = Class.forName("ee.forgr.capacitor_inappbrowser.GeckoViewSession");
            Constructor<?> constructor = geckoSessionClass.getConstructor(
                Context.class,
                int.class,
                Options.class,
                WebViewDialog.PermissionHandler.class,
                WebView.class,
                Activity.class
            );
            return (BrowserSession) constructor.newInstance(context, theme, options, permissionHandler, capacitorWebView, activity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "GeckoView engine was requested but GeckoViewSession is not available in the current Android source set",
                e
            );
        }
    }

    private static void validateGeckoOptions(Options options) {
        List<String> unsupportedFeatures = new ArrayList<>();
        String httpMethod = options.getHttpMethod();

        if (httpMethod != null && !httpMethod.isBlank() && !"GET".equalsIgnoreCase(httpMethod)) {
            unsupportedFeatures.add("non-GET initial requests");
        }
        if (options.getHttpBody() != null && !options.getHttpBody().isBlank()) {
            unsupportedFeatures.add("initial request bodies");
        }
        if (options.getProxyRequestsPattern() != null) {
            unsupportedFeatures.add("proxyRequests");
        }
        if (options.getEnableGooglePaySupport()) {
            unsupportedFeatures.add("Google Pay compatibility mode");
        }

        if (!unsupportedFeatures.isEmpty()) {
            throw new IllegalArgumentException(
                "GeckoView engine does not yet support: " + String.join(", ", unsupportedFeatures)
            );
        }
    }
}
