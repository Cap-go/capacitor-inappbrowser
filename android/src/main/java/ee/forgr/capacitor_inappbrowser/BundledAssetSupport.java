package ee.forgr.capacitor_inappbrowser;

import android.content.Context;
import android.content.res.AssetManager;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceResponse;
import androidx.webkit.WebViewAssetLoader;
import com.getcapacitor.Bridge;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;

final class BundledAssetSupport {

    static final class LocalConfig {

        final String scheme;
        final String host;

        LocalConfig(String scheme, String host) {
            this.scheme = scheme;
            this.host = host;
        }
    }

    static final class Resolution {

        final String url;
        final boolean needsHandler;

        Resolution(String url, boolean needsHandler) {
            this.url = url;
            this.needsHandler = needsHandler;
        }
    }

    private BundledAssetSupport() {}

    static LocalConfig parseLocalConfig(String localUrl) {
        if (localUrl == null || localUrl.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(localUrl.endsWith("/") ? localUrl : localUrl + "/");
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            return new LocalConfig(scheme.toLowerCase(Locale.ROOT), host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    static Resolution resolve(String url, Bridge bridge) {
        String trimmed = url == null ? "" : url.trim();
        LocalConfig localConfig = parseLocalConfig(bridge != null ? bridge.getLocalUrl() : null);
        if (localConfig == null) {
            localConfig = new LocalConfig("https", "localhost");
        }

        if (isRelativeBundledPath(trimmed)) {
            String path = normalizeBundledPath(trimmed);
            return new Resolution(localConfig.scheme + "://" + localConfig.host + path, true);
        }

        if (isBundledLocalUrl(trimmed, localConfig)) {
            return new Resolution(trimmed, true);
        }

        return new Resolution(trimmed, false);
    }

    static boolean isBundledLocalUrl(String url, LocalConfig localConfig) {
        if (!isAbsoluteUrl(url)) {
            return false;
        }

        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return false;
            }
            return scheme.equalsIgnoreCase(localConfig.scheme) && host.equalsIgnoreCase(localConfig.host);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    static boolean isRelativeBundledPath(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return !isAbsoluteUrl(url);
    }

    static String normalizeBundledPath(String path) {
        String trimmed = path == null ? "" : path.trim();
        if (trimmed.isEmpty() || "/".equals(trimmed)) {
            return "/";
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    static WebViewAssetLoader createAssetLoader(Context context, String hostname) {
        return new WebViewAssetLoader.Builder()
            .setDomain(hostname)
            .addPathHandler("/", new PublicAssetsPathHandler(context.getAssets()))
            .build();
    }

    static String mimeTypeForPath(String path) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(path);
        if (extension != null && !extension.isEmpty()) {
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.ROOT));
            if (mimeType != null) {
                return mimeType;
            }
        }
        return extension == null || extension.isEmpty() ? "text/html" : "application/octet-stream";
    }

    private static boolean isAbsoluteUrl(String url) {
        if (url.startsWith("//")) {
            return true;
        }

        int separatorIndex = url.indexOf(':');
        if (separatorIndex <= 0) {
            return false;
        }

        for (int index = 0; index < separatorIndex; index++) {
            char character = url.charAt(index);
            if (!Character.isLetterOrDigit(character) && character != '+' && character != '-' && character != '.') {
                return false;
            }
        }

        return Character.isLetter(url.charAt(0));
    }

    private static String mapToAssetPath(String path) {
        String normalizedPath = path == null || path.isEmpty() ? "/" : path;
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }

        int lastSlash = normalizedPath.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? normalizedPath.substring(lastSlash + 1) : normalizedPath;
        if (!fileName.contains(".")) {
            return "public/index.html";
        }

        return "public" + normalizedPath;
    }

    private static final class PublicAssetsPathHandler implements WebViewAssetLoader.PathHandler {

        private final AssetManager assetManager;

        PublicAssetsPathHandler(AssetManager assetManager) {
            this.assetManager = assetManager;
        }

        @Override
        public WebResourceResponse handle(String path) {
            String assetPath = mapToAssetPath(path);
            try {
                InputStream stream = assetManager.open(assetPath);
                return new WebResourceResponse(mimeTypeForPath(path), null, stream);
            } catch (IOException primaryError) {
                if (!"public/index.html".equals(assetPath)) {
                    try {
                        InputStream fallbackStream = assetManager.open("public/index.html");
                        return new WebResourceResponse("text/html", null, fallbackStream);
                    } catch (IOException ignored) {
                        return null;
                    }
                }
                return null;
            }
        }
    }
}
