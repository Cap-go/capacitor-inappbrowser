package ee.forgr.capacitor_inappbrowser;

import org.json.JSONObject;

public interface BrowserSessionProxy {
    void handleProxyResultError(String result, String id);

    void handleProxyResultOk(JSONObject result, String id);
}
