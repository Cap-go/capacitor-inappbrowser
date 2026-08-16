package ee.forgr.capacitor_inappbrowser;

import android.content.Intent;
import android.webkit.MimeTypeMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

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
        LinkedHashSet<String> mimeTypes = normalizeAcceptTypes(acceptTypes);
        if (mimeTypes.isEmpty()) {
            return false;
        }
        for (String mime : mimeTypes) {
            if (!mime.startsWith("image/")) {
                return false;
            }
        }
        return true;
    }

    static boolean isMediaOnlyMimeSet(Set<String> mimeTypes) {
        if (mimeTypes.isEmpty()) {
            return false;
        }
        for (String mime : mimeTypes) {
            if (!(mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/"))) {
                return false;
            }
        }
        return true;
    }

    static Intent createFileChooserIntent(Set<String> mimeTypes, boolean multiple) {
        boolean mediaOnly = isMediaOnlyMimeSet(mimeTypes);
        Intent intent = new Intent(mediaOnly ? Intent.ACTION_GET_CONTENT : Intent.ACTION_OPEN_DOCUMENT);
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
