package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HtmlDataUrlSupportTest {

    @Test
    public void parsesHtmlBase64DataUrl() {
        String html = "<html><body><form id=\"loginForm\"></form></body></html>";
        String url = "data:text/html;base64,PGh0bWw+PGJvZHk+PGZvcm0gaWQ9ImxvZ2luRm9ybSI+PC9mb3JtPjwvYm9keT48L2h0bWw+";

        assertTrue(HtmlDataUrlSupport.isHtmlBase64DataUrl(url));
        assertEquals(html, HtmlDataUrlSupport.parseHtml(url));
    }

    @Test
    public void rejectsNonHtmlDataUrls() {
        assertFalse(HtmlDataUrlSupport.isHtmlBase64DataUrl("data:image/png;base64,abc"));
        assertNull(HtmlDataUrlSupport.parseHtml("https://example.com"));
    }
}
