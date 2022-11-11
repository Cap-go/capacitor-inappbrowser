package ee.forgr.capacitor_inappbrowser;

import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;

public class Options {

    private String title;
    private String url;
    private JSObject headers;
    private String toolbarType;
    private JSObject shareDisclaimer;
    private String shareSubject;
    private boolean isPresentAfterPageLoad;
    private WebViewCallbacks callbacks;
    private PluginCall pluginCall;

    public PluginCall getPluginCall() {
        return pluginCall;
    }

    public void setPluginCall(PluginCall pluginCall) {
        this.pluginCall = pluginCall;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public JSObject getHeaders() {
        return headers;
    }

    public void setHeaders(JSObject headers) {
        this.headers = headers;
    }

    public String getToolbarType() {
        return toolbarType;
    }

    public void setToolbarType(String toolbarType) {
        this.toolbarType = toolbarType;
    }

    public JSObject getShareDisclaimer() {
        return shareDisclaimer;
    }

    public void setShareDisclaimer(JSObject shareDisclaimer) {
        this.shareDisclaimer = shareDisclaimer;
    }

    public String getShareSubject() {
        return shareSubject;
    }

    public void setShareSubject(String shareSubject) {
        this.shareSubject = shareSubject;
    }

    public boolean isPresentAfterPageLoad() {
        return isPresentAfterPageLoad;
    }

    public void setPresentAfterPageLoad(boolean presentAfterPageLoad) {
        isPresentAfterPageLoad = presentAfterPageLoad;
    }

    public WebViewCallbacks getCallbacks() {
        return callbacks;
    }

    public void setCallbacks(WebViewCallbacks callbacks) {
        this.callbacks = callbacks;
    }
}
