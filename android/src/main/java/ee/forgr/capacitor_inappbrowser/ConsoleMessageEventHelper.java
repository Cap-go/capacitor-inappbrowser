package ee.forgr.capacitor_inappbrowser;

import com.getcapacitor.JSObject;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class ConsoleMessageEventHelper {

    private ConsoleMessageEventHelper() {}

    static JSObject create(String webViewId, String level, String message, String source, Integer line, Integer column) {
        Map<String, Object> data = createData(webViewId, level, message, source, line, column);
        JSObject event = new JSObject();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            event.put(entry.getKey(), entry.getValue());
        }
        return event;
    }

    static Map<String, Object> createData(String webViewId, String level, String message, String source, Integer line, Integer column) {
        Map<String, Object> event = new HashMap<>();
        if (webViewId != null && !webViewId.isEmpty()) {
            event.put("id", webViewId);
        }
        event.put("level", normalizeLevel(level));
        event.put("message", message != null ? message : "");
        if (source != null && !source.isEmpty()) {
            event.put("source", source);
        }
        if (line != null) {
            event.put("line", line);
        }
        if (column != null) {
            event.put("column", column);
        }
        return event;
    }

    static String normalizeLevel(String level) {
        if (level == null) {
            return "log";
        }

        String normalized = level.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "log", "info", "warn", "error", "debug" -> normalized;
            case "tip" -> "info";
            case "warning" -> "warn";
            case "assert" -> "error";
            default -> "log";
        };
    }
}
