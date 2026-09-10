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

    /**
     * Keeps the layout's edge nudge pointing outwards for the resolved gravity.
     * Resolves start/end against the layout direction so RTL mirrors correctly.
     */
    static float mirroredTranslationX(int gravity, int layoutDirection, float translationX) {
        int absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection);
        float outwards = Math.abs(translationX);
        return absoluteGravity == Gravity.RIGHT ? outwards : -outwards;
    }
}
