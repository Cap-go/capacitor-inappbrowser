package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ToolbarNativeUIEngineSupportTest {

    @Test
    public void staysOffUnlessExplicitlyEnabled() {
        assertFalse(ToolbarNativeUIEngineSupport.shouldRegister(false, ""));
        assertFalse(ToolbarNativeUIEngineSupport.shouldRegister(false, "navigation"));
    }

    @Test
    public void rejectsBlankToolbar() {
        assertFalse(ToolbarNativeUIEngineSupport.shouldRegister(true, "blank"));
        assertTrue(ToolbarNativeUIEngineSupport.shouldRegister(true, "BLANK"));
    }

    @Test
    public void acceptsDefaultCustomToolbarTypes() {
        assertTrue(ToolbarNativeUIEngineSupport.shouldRegister(true, ""));
        assertTrue(ToolbarNativeUIEngineSupport.shouldRegister(true, "navigation"));
    }

    @Test
    public void validatesSelectorWhenEnabled() {
        assertNull(ToolbarNativeUIEngineSupport.validateOptions(false, null));
        assertEquals(
            "nativeUIProjectionSelector is required when enableNativeUIEngine is true",
            ToolbarNativeUIEngineSupport.validateOptions(true, null)
        );
        assertNull(ToolbarNativeUIEngineSupport.validateOptions(true, "toolbar-slot"));
        assertNull(ToolbarNativeUIEngineSupport.validateOptions(true, "#toolbar-slot"));
    }
}
