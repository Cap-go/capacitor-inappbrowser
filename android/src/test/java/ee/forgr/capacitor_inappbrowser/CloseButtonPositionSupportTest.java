package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.res.Configuration;
import android.view.Gravity;
import android.view.View;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CloseButtonPositionSupportTest {

    @Test
    public void mapsOptionValuesToGravity() {
        assertEquals(Integer.valueOf(Gravity.START), CloseButtonPositionSupport.gravityFor("start"));
        assertEquals(Integer.valueOf(Gravity.END), CloseButtonPositionSupport.gravityFor("end"));
    }

    @Test
    public void keepsLayoutDefaultWhenUnsetOrUnknown() {
        assertNull(CloseButtonPositionSupport.gravityFor(null));
        assertNull(CloseButtonPositionSupport.gravityFor("left"));
    }

    @Test
    public void nudgeAlwaysPointsOutwardsInLtr() {
        assertEquals(-8f, CloseButtonPositionSupport.mirroredTranslationX(Gravity.START, View.LAYOUT_DIRECTION_LTR, -8f), 0f);
        assertEquals(8f, CloseButtonPositionSupport.mirroredTranslationX(Gravity.END, View.LAYOUT_DIRECTION_LTR, -8f), 0f);
        assertEquals(8f, CloseButtonPositionSupport.mirroredTranslationX(Gravity.END, View.LAYOUT_DIRECTION_LTR, 8f), 0f);
    }

    @Test
    public void nudgeAlwaysPointsOutwardsInRtl() {
        assertEquals(8f, CloseButtonPositionSupport.mirroredTranslationX(Gravity.START, View.LAYOUT_DIRECTION_RTL, -8f), 0f);
        assertEquals(-8f, CloseButtonPositionSupport.mirroredTranslationX(Gravity.END, View.LAYOUT_DIRECTION_RTL, -8f), 0f);
    }

    @Test
    public void nudgeUsesConfigurationLayoutDirectionForRtlLocales() {
        Configuration configuration = RuntimeEnvironment.getApplication().getResources().getConfiguration();
        configuration.setLocale(Locale.forLanguageTag("ar"));
        configuration.setLayoutDirection(Locale.forLanguageTag("ar"));

        int layoutDirection = configuration.getLayoutDirection();
        assertEquals(View.LAYOUT_DIRECTION_RTL, layoutDirection);
        assertEquals(8f, CloseButtonPositionSupport.mirroredTranslationX(Gravity.START, layoutDirection, -8f), 0f);
        assertEquals(-8f, CloseButtonPositionSupport.mirroredTranslationX(Gravity.END, layoutDirection, -8f), 0f);
    }
}
