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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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
        final boolean needsAssetLoader;

        Resolution(String url) {
            this(url, false);
        }

        Resolution(String url, boolean needsAssetLoader) {
            this.url = url;
            this.needsAssetLoader = needsAssetLoader;
        }
    }

    private static final String[] RESERVED_WEB_SCHEMES = { "http", "https" };
    private static final String PROXY_BRIDGE_MARKER_PATH = "/_capgo_proxy_";

    private BundledAssetSupport() {}

    static LocalConfig parseLocalConfig(String localUrl) {
        if (isBlank(localUrl)) {
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

    static String assetLoaderScheme(LocalConfig localConfig) {
        if ("http".equals(localConfig.scheme) || "https".equals(localConfig.scheme)) {
            return localConfig.scheme;
        }
        return "https";
    }

    static Resolution resolve(String url, Bridge bridge) {
        LocalConfig localConfig = parseLocalConfig(bridge != null ? bridge.getLocalUrl() : null);
        if (localConfig == null) {
            localConfig = new LocalConfig("https", "localhost");
        }
        return resolve(url, localConfig);
    }

    static Resolution resolve(String url, LocalConfig localConfig) {
        String trimmed = url == null ? "" : url.trim();
        String navigationScheme = assetLoaderScheme(localConfig);

        if (isRelativeBundledPath(trimmed)) {
            String path = normalizeBundledPath(trimmed);
            if (path == null) {
                return null;
            }
            return new Resolution(navigationScheme + "://" + localConfig.host + path, true);
        }

        if (isBundledLocalUrl(trimmed, localConfig)) {
            String rewritten = rewriteBundledLocalUrl(trimmed, localConfig, navigationScheme);
            return new Resolution(rewritten != null ? rewritten : trimmed, true);
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

            if (!host.equalsIgnoreCase(localConfig.host)) {
                return false;
            }

            if (uri.getPort() != -1) {
                return false;
            }

            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (normalizedScheme.equalsIgnoreCase(localConfig.scheme) || normalizedScheme.equals(assetLoaderScheme(localConfig))) {
                return true;
            }

            return isReservedWebScheme(normalizedScheme) && isReservedWebScheme(localConfig.scheme);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    static boolean isRelativeBundledPath(String url) {
        if (isBlank(url)) {
            return false;
        }
        return !isAbsoluteUrl(url);
    }

    private static final Set<String> BUNDLED_ASSET_EXTENSIONS = Collections.unmodifiableSet(
        new HashSet<>(
            Arrays.asList(
                "html",
                "htm",
                "js",
                "css",
                "json",
                "xml",
                "svg",
                "png",
                "jpg",
                "jpeg",
                "gif",
                "webp",
                "woff",
                "woff2",
                "ttf",
                "map"
            )
        )
    );

    static boolean isLikelyBundledRelativePath(String url) {
        if (!isRelativeBundledPath(url)) {
            return false;
        }

        String trimmed = stripPathQueryAndFragment(url.trim());
        if (finalPathComponentStartsWithDot(trimmed)) {
            return false;
        }

        if (trimmed.startsWith("/") || trimmed.contains("/")) {
            return true;
        }

        int dot = trimmed.lastIndexOf('.');
        if (dot < 0) {
            return true;
        }
        if (dot == 0) {
            return false;
        }

        String extension = trimmed.substring(dot + 1).toLowerCase(Locale.ROOT);
        return BUNDLED_ASSET_EXTENSIONS.contains(extension);
    }

    private static boolean isBlank(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }

        for (int index = 0; index < value.length(); index++) {
            if (!Character.isWhitespace(value.charAt(index))) {
                return false;
            }
        }

        return true;
    }

    private static boolean finalPathComponentStartsWithDot(String path) {
        int lastSlash = path.lastIndexOf('/');
        String finalComponent = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        return !finalComponent.isEmpty() && finalComponent.charAt(0) == '.';
    }

    private static String stripPathQueryAndFragment(String path) {
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int fragment = path.indexOf('#');
        if (fragment >= 0) {
            path = path.substring(0, fragment);
        }
        return path;
    }

    static String normalizeBundledPath(String path) {
        String trimmed = path == null ? "" : path.trim();
        if (trimmed.isEmpty() || "/".equals(trimmed)) {
            return "/";
        }

        if (containsPathTraversal(trimmed)) {
            return null;
        }

        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    static boolean isProxyBridgeMarkerPath(String path) {
        return PROXY_BRIDGE_MARKER_PATH.equals(path);
    }

    static WebViewAssetLoader createAssetLoader(Context context, String hostname, String scheme) {
        WebViewAssetLoader.Builder builder = new WebViewAssetLoader.Builder()
            .setDomain(hostname)
            .addPathHandler("/", new PublicAssetsPathHandler(context.getAssets()));
        if ("http".equalsIgnoreCase(scheme)) {
            builder.setHttpAllowed(true);
        }
        return builder.build();
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

    static String encodingForMimeType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return null;
        }

        String normalized = mimeType.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("text/")) {
            return "utf-8";
        }
        if (
            normalized.contains("javascript") ||
            normalized.contains("json") ||
            normalized.contains("xml") ||
            normalized.endsWith("+json") ||
            normalized.endsWith("+xml")
        ) {
            return "utf-8";
        }
        return null;
    }

    private static WebResourceResponse notFoundResponse(String path) {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, new java.io.ByteArrayInputStream(new byte[0]));
    }

    private static boolean isReservedWebScheme(String scheme) {
        for (String reservedScheme : RESERVED_WEB_SCHEMES) {
            if (reservedScheme.equals(scheme)) {
                return true;
            }
        }
        return false;
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

    private static boolean containsPathTraversal(String path) {
        for (String segment : path.split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static String rewriteBundledLocalUrl(String url, LocalConfig localConfig, String navigationScheme) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || !host.equalsIgnoreCase(localConfig.host)) {
                return null;
            }
            if (scheme.equalsIgnoreCase(navigationScheme)) {
                return null;
            }
            return new URI(
                navigationScheme,
                uri.getUserInfo(),
                host,
                uri.getPort(),
                uri.getPath(),
                uri.getQuery(),
                uri.getFragment()
            ).toString();
        } catch (Exception error) {
            return null;
        }
    }

    private static String mapToAssetPath(String path) {
        if (containsPathTraversal(path)) {
            return null;
        }

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
            if (isProxyBridgeMarkerPath(path)) {
                return null;
            }

            String assetPath = mapToAssetPath(path);
            if (assetPath == null) {
                return null;
            }

            try {
                InputStream stream = assetManager.open(assetPath);
                String mimeType = mimeTypeForPath(path);
                return new WebResourceResponse(mimeType, encodingForMimeType(mimeType), stream);
            } catch (IOException error) {
                return notFoundResponse(path);
            }
        }
    }
}
