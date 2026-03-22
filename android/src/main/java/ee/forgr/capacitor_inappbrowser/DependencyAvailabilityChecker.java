package ee.forgr.capacitor_inappbrowser;

import android.util.Log;

public final class DependencyAvailabilityChecker {

    private static final String LOG_TAG = "DependencyAvailabilityChecker";

    private static Boolean geckoDependenciesAvailable = null;

    private DependencyAvailabilityChecker() {}

    public static boolean isGeckoAvailable() {
        if (geckoDependenciesAvailable != null) {
            return geckoDependenciesAvailable;
        }

        String[] geckoClasses = {
            "org.mozilla.geckoview.GeckoRuntime",
            "org.mozilla.geckoview.GeckoSession",
            "org.mozilla.geckoview.GeckoView"
        };

        boolean allAvailable = true;
        for (String className : geckoClasses) {
            if (!isClassAvailable(className)) {
                allAvailable = false;
                Log.w(LOG_TAG, "Gecko dependency class not available: " + className);
            }
        }

        geckoDependenciesAvailable = allAvailable;
        return allAvailable;
    }

    private static boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
