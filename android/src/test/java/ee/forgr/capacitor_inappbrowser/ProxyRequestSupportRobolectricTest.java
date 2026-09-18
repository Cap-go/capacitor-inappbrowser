package ee.forgr.capacitor_inappbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.getcapacitor.JSObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ProxyRequestSupportRobolectricTest {

    @Test
    public void copyProxyDecisionCreatesIndependentSnapshotForNestedRequestAndResponse() {
        JSObject requestHeaders = new JSObject();
        requestHeaders.put("Authorization", "Bearer original-token");

        JSObject request = new JSObject();
        request.put("url", "https://api.example.com/v1/items");
        request.put("method", "POST");
        request.put("body", "original-body");
        request.put("headers", requestHeaders);

        JSObject responseHeaders = new JSObject();
        responseHeaders.put("Content-Type", "application/json");

        JSObject response = new JSObject();
        response.put("status", 200);
        response.put("body", "original-response");
        response.put("headers", responseHeaders);

        JSObject decision = new JSObject();
        decision.put("cancel", false);
        decision.put("request", request);
        decision.put("response", response);

        JSObject copy = ProxyRequestSupport.copyProxyDecision(decision);
        assertNotNull(copy);

        decision.put("cancel", true);
        request.put("url", "https://api.example.com/v1/mutated");
        request.put("method", "GET");
        request.put("body", "mutated-body");
        requestHeaders.put("Authorization", "Bearer mutated-token");
        response.put("status", 404);
        response.put("body", "mutated-response");
        responseHeaders.put("Content-Type", "text/plain");

        assertFalse(copy.getBool("cancel"));
        JSObject copiedRequest = copy.getJSObject("request");
        assertNotNull(copiedRequest);
        assertEquals("https://api.example.com/v1/items", copiedRequest.getString("url"));
        assertEquals("POST", copiedRequest.getString("method"));
        assertEquals("original-body", copiedRequest.getString("body"));
        assertEquals("Bearer original-token", copiedRequest.getJSObject("headers").getString("Authorization"));

        JSObject copiedResponse = copy.getJSObject("response");
        assertNotNull(copiedResponse);
        assertEquals(Integer.valueOf(200), copiedResponse.getInteger("status"));
        assertEquals("original-response", copiedResponse.getString("body"));
        assertEquals("application/json", copiedResponse.getJSObject("headers").getString("Content-Type"));
    }
}
