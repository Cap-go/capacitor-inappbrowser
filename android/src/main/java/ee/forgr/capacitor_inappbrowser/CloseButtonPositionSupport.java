package ee.forgr.capacitor_inappbrowser;

import android.view.Gravity;

/**
 * Maps the {@code closeButtonPosition} option onto toolbar layout values.
 */
final class CloseButtonPositionSupport {

    /** End padding that visually matches the start side's toolbar content inset. */
    static final int END_PADDING_DP = 20;

    private CloseButtonPositionSupport() {}

    /** Toolbar gravity for the option value, or {@code null} to keep the layout default. */
    static Integer gravityFor(String closeButtonPosition) {
        if ("start".equals(closeButtonPosition)) {
            return Gravity.START;
        }
        if ("end".equals(closeButtonPosition)) {
            return Gravity.END;
        }
        return null;
    }
}
