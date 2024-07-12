package ee.forgr.capacitor_inappbrowser;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;

public class Options {

  private String title;
  private boolean CloseModal;
  private String CloseModalTitle;
  private String CloseModalDescription;
  private String CloseModalCancel;
  private String CloseModalOk;
  private String url;
  private JSObject headers;
  private String toolbarType;
  private JSObject shareDisclaimer;
  private String shareSubject;
  private boolean disableGoBackOnNativeApplication;
  private boolean activeNativeNavigationForWebview;
  private boolean isPresentAfterPageLoad;
  private WebViewCallbacks callbacks;
  private PluginCall pluginCall;
  private boolean VisibleTitle;
  private String ToolbarColor;
  private boolean ShowArrow;
  private boolean ignoreUntrustedSSLError;
  private boolean useWhitePanelMode;
  private boolean useHardwareAcceleration;
  private JSArray autoClosePatterns;
  private JSArray openSystemBrowserList;

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

  public boolean getCloseModal() {
    return CloseModal;
  }

  public void setCloseModal(boolean CloseModal) {
    this.CloseModal = CloseModal;
  }

  public String getCloseModalTitle() {
    return CloseModalTitle;
  }

  public void setCloseModalTitle(String CloseModalTitle) {
    this.CloseModalTitle = CloseModalTitle;
  }

  public String getCloseModalDescription() {
    return CloseModalDescription;
  }

  public void setCloseModalDescription(String CloseModalDescription) {
    this.CloseModalDescription = CloseModalDescription;
  }

  public String getCloseModalCancel() {
    return CloseModalCancel;
  }

  public void setCloseModalCancel(String CloseModalCancel) {
    this.CloseModalCancel = CloseModalCancel;
  }

  public String getCloseModalOk() {
    return CloseModalOk;
  }

  public void setCloseModalOk(String CloseModalOk) {
    this.CloseModalOk = CloseModalOk;
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

  public boolean showReloadButton;

  public boolean getShowReloadButton() {
    return showReloadButton;
  }

  public void setShowReloadButton(boolean showReloadButton) {
    this.showReloadButton = showReloadButton;
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

  public boolean getActiveNativeNavigationForWebview() {
    return activeNativeNavigationForWebview;
  }

  public void setActiveNativeNavigationForWebview(
    boolean activeNativeNavigationForWebview
  ) {
    this.activeNativeNavigationForWebview = activeNativeNavigationForWebview;
  }

  public boolean getDisableGoBackOnNativeApplication() {
    return disableGoBackOnNativeApplication;
  }

  public void setDisableGoBackOnNativeApplication(
    boolean disableGoBackOnNativeApplication
  ) {
    this.disableGoBackOnNativeApplication = disableGoBackOnNativeApplication;
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

  public boolean getVisibleTitle() {
    return VisibleTitle;
  }

  public void setVisibleTitle(boolean _visibleTitle) {
    this.VisibleTitle = _visibleTitle;
  }

  public String getToolbarColor() {
    return ToolbarColor;
  }

  public void setToolbarColor(String toolbarColor) {
    this.ToolbarColor = toolbarColor;
  }

  public boolean showArrow() {
    return ShowArrow;
  }

  public void setArrow(boolean _showArrow) {
    this.ShowArrow = _showArrow;
  }

  public boolean ignoreUntrustedSSLError() {
    return ignoreUntrustedSSLError;
  }

  public void setIgnoreUntrustedSSLError(boolean _ignoreUntrustedSSLError) {
    this.ignoreUntrustedSSLError = _ignoreUntrustedSSLError;
  }

  public boolean useWhitePanelMode() {
    return useWhitePanelMode;
  }

  public void setUseWhitePanelMode(boolean _useWhitePanelMode) {
    this.useWhitePanelMode = _useWhitePanelMode;
  }

  public boolean useHardwareAcceleration() {
    return useHardwareAcceleration;
  }

  public void setUseHardwareAcceleration(boolean _useHardwareAcceleration) {
    this.useHardwareAcceleration = _useHardwareAcceleration;
  }

  public JSArray getAutoClosePatterns() {
    return autoClosePatterns;
  }

  public void setAutoClosePatterns(JSArray autoClosePatterns) {
    this.autoClosePatterns = autoClosePatterns;
  }

  public void setOpenSystemBrowserList(JSArray openSystemBrowserList) {
    this.openSystemBrowserList = openSystemBrowserList;
  }

  public JSArray getOpenSystemBrowserList() {
    return openSystemBrowserList;
  }
}
