package ee.forgr.capacitor_inappbrowser;

import android.content.res.AssetManager;
import androidx.annotation.Nullable;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class Options {

  public static class ButtonNearDone {

    public enum AllIconTypes {
      ASSET,
    }

    private AllIconTypes iconType;
    private String icon;
    private int height;
    private int width;

    private ButtonNearDone(
      AllIconTypes iconType,
      String icon,
      int height,
      int width
    ) {
      this.iconType = iconType;
      this.icon = icon;
      this.height = height;
      this.width = width;
    }

    @Nullable
    public static ButtonNearDone generateFromPluginCall(
      PluginCall call,
      AssetManager assetManager
    ) throws IllegalArgumentException, RuntimeException {
      JSObject buttonNearDone = call.getObject("buttonNearDone");
      if (buttonNearDone == null) {
        // Return null when "buttonNearDone" isn't configured, else throw an error
        return null;
      }

      JSObject android = buttonNearDone.getJSObject("android");
      if (android == null) {
        throw new IllegalArgumentException("buttonNearDone.android is null");
      }

      String iconType = android.getString("iconType", "asset");
      if (!Objects.equals(iconType, "asset")) {
        throw new IllegalArgumentException(
          "buttonNearDone.android.iconType is not equal to \"asset\""
        );
      }

      String icon = android.getString("icon");
      if (icon == null) {
        throw new IllegalArgumentException(
          "buttonNearDone.android.icon is null"
        );
      }

      InputStream fileInputString = null;

      try {
        // Try to open the file
        fileInputString = assetManager.open(icon);
        // do nothing
      } catch (IOException e) {
        throw new IllegalArgumentException(
          "buttonNearDone.android.icon cannot be found in the assetManager"
        );
      } finally {
        // Close the input stream if it was opened
        if (fileInputString != null) {
          try {
            fileInputString.close();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }
      Integer width = android.getInteger("width", 48);

      Integer height = android.getInteger("height", 48);
      return new ButtonNearDone(AllIconTypes.ASSET, icon, height, width);
    }

    public AllIconTypes getIconType() {
      return iconType;
    }

    public String getIcon() {
      return icon;
    }

    public int getHeight() {
      return height;
    }

    public int getWidth() {
      return width;
    }
  }

  private String title;
  private boolean CloseModal;
  private String CloseModalTitle;
  private String CloseModalDescription;
  private String CloseModalCancel;
  private ButtonNearDone buttonNearDone;
  private String CloseModalOk;
  private String url;
  private JSObject headers;
  private JSObject credentials;
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
  private String preShowScript;

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

  public void setButtonNearDone(ButtonNearDone buttonNearDone) {
    this.buttonNearDone = buttonNearDone;
  }

  public ButtonNearDone getButtonNearDone() {
    return this.buttonNearDone;
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

  public JSObject getCredentials() {
    return credentials;
  }

  public void setCredentials(JSObject credentials) {
    this.credentials = credentials;
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

  public String getPreShowScript() {
    return preShowScript;
  }

  public void setPreShowScript(String preLoadScript) {
    this.preShowScript = preLoadScript;
  }
}
