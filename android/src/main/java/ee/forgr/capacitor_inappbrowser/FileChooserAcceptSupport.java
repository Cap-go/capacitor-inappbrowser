package ee.forgr.capacitor_inappbrowser;

import android.content.Intent;
import android.webkit.MimeTypeMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Normalizes WebView file-input accept attributes into Android file-chooser intents.
 */
final class FileChooserAcceptSupport {

    private FileChooserAcceptSupport() {}

    static String acceptEntryToMimeType(String accept) {
        if (accept == null || accept.isEmpty() || accept.equals("undefined")) {
            return null;
        }
        accept = accept.toLowerCase(Locale.ROOT);
        if (accept.contains("/")) {
            return accept;
        }
        if (!accept.startsWith(".")) {
            return null;
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(accept.substring(1).toLowerCase(Locale.ROOT));
    }

    static LinkedHashSet<String> normalizeAcceptTypes(String[] acceptTypes) {
        LinkedHashSet<String> mimeTypes = new LinkedHashSet<>();
        if (acceptTypes != null) {
            for (String entry : acceptTypes) {
                if (entry == null) {
                    continue;
                }
                for (String part : entry.split(",")) {
                    String mime = acceptEntryToMimeType(part.trim());
                    if (mime != null) {
                        mimeTypes.add(mime);
                    }
                }
            }
        }
        if (mimeTypes.contains("*/*")) {
            mimeTypes.clear();
        }
        return mimeTypes;
    }

    static boolean isImageOnlyAcceptTypes(String[] acceptTypes) {
        return matchesAllResolvedAcceptTypes(acceptTypes, (mime) -> mime.startsWith("image/"));
    }

    static boolean isMediaOnlyAcceptTypes(String[] acceptTypes) {
        return matchesAllResolvedAcceptTypes(
            acceptTypes,
            (mime) -> mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/")
        );
    }

    private static boolean matchesAllResolvedAcceptTypes(String[] acceptTypes, Predicate<String> mimePredicate) {
        boolean foundMatch = false;
        if (acceptTypes == null) {
            return false;
        }
        for (String entry : acceptTypes) {
            if (entry == null) {
                continue;
            }
            for (String part : entry.split(",")) {
                String token = part.trim();
                if (token.isEmpty() || token.equals("undefined")) {
                    continue;
                }
                String mime = acceptEntryToMimeType(token);
                if (mime == null || mime.equals("*/*")) {
                    return false;
                }
                if (!mimePredicate.test(mime)) {
                    return false;
                }
                foundMatch = true;
            }
        }
        return foundMatch;
    }

    static boolean useGetContentAction(String[] acceptTypes) {
        LinkedHashSet<String> mimeTypes = normalizeAcceptTypes(acceptTypes);
        return isMediaOnlyAcceptTypes(acceptTypes) && mimeTypes.size() == 1;
    }

    static Intent createFileChooserIntent(String[] acceptTypes, boolean multiple) {
        LinkedHashSet<String> mimeTypes = normalizeAcceptTypes(acceptTypes);
        Intent intent = new Intent(useGetContentAction(acceptTypes) ? Intent.ACTION_GET_CONTENT : Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        if (mimeTypes.size() == 1) {
            intent.setType(mimeTypes.iterator().next());
        } else {
            intent.setType("*/*");
            if (!mimeTypes.isEmpty()) {
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toArray(new String[0]));
            }
        }

        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
        return intent;
    }
}
