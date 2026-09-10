package ee.forgr.capacitor_inappbrowser;

import android.view.Gravity;

/**
 * Maps the {@code closeButtonPosition} option onto toolbar layout values.
 */
final class CloseButtonPositionSupport {

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

    /** Keeps the layout's edge nudge pointing outwards for the resolved gravity. */
    static float mirroredTranslationX(int gravity, float translationX) {
        float outwards = Math.abs(translationX);
        return gravity == Gravity.END ? outwards : -outwards;
    }
}
