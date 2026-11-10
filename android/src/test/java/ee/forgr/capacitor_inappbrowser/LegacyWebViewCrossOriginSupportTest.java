package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.*;

import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.activity.ComponentActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LegacyWebViewCrossOriginSupportTest {

    @Test
    @SuppressWarnings("deprecation")
    public void trustedBundledFileAccessAppliesOnApi30AndAbove() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        WebSettings settings = new WebView(activity).getSettings();
        LegacyWebViewCrossOriginSupport.applyTrustedBundledFileAccess(settings, true);
        assertTrue(settings.getAllowFileAccessFromFileURLs());
        assertTrue(settings.getAllowUniversalAccessFromFileURLs());

        LegacyWebViewCrossOriginSupport.applyTrustedBundledFileAccess(settings, false);
        assertFalse(settings.getAllowFileAccessFromFileURLs());
        assertFalse(settings.getAllowUniversalAccessFromFileURLs());
    }
}
