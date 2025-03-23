package ee.forgr.capacitor_inappbrowser;

import android.content.res.AssetManager;
import androidx.annotation.Nullable;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.regex.Pattern;

public class Options {

  public static class ButtonNearDone {

    public enum AllIconTypes {
      ASSET,
      VECTOR,
    }

    private final AllIconTypes iconTypeEnum;
    private final String iconType;
    private final String icon;
    private final int height;
    private final int width;

    private ButtonNearDone(
      AllIconTypes iconTypeEnum,
      String iconType,
      String icon,
      int height,
      int width
    ) {
      this.iconTypeEnum = iconTypeEnum;
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
      AllIconTypes iconTypeEnum;

      // Validate and process icon type
      if ("asset".equals(iconType)) {
        iconTypeEnum = AllIconTypes.ASSET;
      } else if ("vector".equals(iconType)) {
        iconTypeEnum = AllIconTypes.VECTOR;
      } else {
        throw new IllegalArgumentException(
          "buttonNearDone.android.iconType must be 'asset' or 'vector'"
        );
      }

      String icon = android.getString("icon");
      if (icon == null) {
        throw new IllegalArgumentException(
          "buttonNearDone.android.icon is null"
        );
      }

      // For asset type, verify the file exists
      if (iconTypeEnum == AllIconTypes.ASSET) {
        InputStream fileInputString = null;

        try {
          // Try to find in public folder first
          try {
            fileInputString = assetManager.open("public/" + icon);
          } catch (IOException e) {
            // If not in public, try in root assets
            try {
              fileInputString = assetManager.open(icon);
            } catch (IOException e2) {
              throw new IllegalArgumentException(
                "buttonNearDone.android.icon cannot be found in the assetManager"
              );
            }
          }
          // File exists, do nothing
        } finally {
          // Close the input stream if it was opened
          if (fileInputString != null) {
            try {
              fileInputString.close();
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        }
      }
      // For vector type, we don't validate here since resources are checked at runtime
      else if (iconTypeEnum == AllIconTypes.VECTOR) {
        // Vector resources will be validated when used
        System.out.println(
          "Vector resource will be validated at runtime: " + icon
        );
      }

      Integer width = android.getInteger("width", 24);
      Integer height = android.getInteger("height", 24);

      final ButtonNearDone buttonNearDone1 = new ButtonNearDone(
        iconTypeEnum,
        iconType,
        icon,
        height,
        width
      );
      return buttonNearDone1;
    }

    public AllIconTypes getIconTypeEnum() {
      return iconTypeEnum;
    }

    public String getIconType() {
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
  private String toolbarTextColor;
  private Pattern proxyRequestsPattern = null;
  private boolean materialPicker = false;
  private int textZoom = 100; // Default text zoom is 100%

  public int getTextZoom() {
    return textZoom;
  }

  public void setTextZoom(int textZoom) {
    this.textZoom = textZoom;
  }

  public boolean getMaterialPicker() {
    return materialPicker;
  }

  public void setMaterialPicker(boolean materialPicker) {
    this.materialPicker = materialPicker;
  }

  public Pattern getProxyRequestsPattern() {
    return proxyRequestsPattern;
  }

  public void setProxyRequestsPattern(Pattern proxyRequestsPattern) {
    this.proxyRequestsPattern = proxyRequestsPattern;
  }

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

  public String getToolbarTextColor() {
    return toolbarTextColor;
  }

  public void setToolbarTextColor(String toolbarTextColor) {
    this.toolbarTextColor = toolbarTextColor;
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
