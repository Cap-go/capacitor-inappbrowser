package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.view.Gravity;
import org.junit.Test;

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
    public void nudgeAlwaysPointsOutwards() {
        assertEquals(-8f, CloseButtonPositionSupport.mirroredTranslationX(Gravity.START, -8f), 0f);
        assertEquals(8f, CloseButtonPositionSupport.mirroredTranslationX(Gravity.END, -8f), 0f);
        assertEquals(8f, CloseButtonPositionSupport.mirroredTranslationX(Gravity.END, 8f), 0f);
    }
}
