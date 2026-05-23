package ee.forgr.capacitor_inappbrowser;

final class MobileAppBridgeScript {

    static final String POST_MESSAGE_BRIDGE_NAME = "__capgoInAppBrowserPostMessageBridge";

    private MobileAppBridgeScript() {}

    static String create(boolean allowJavaScriptControl, boolean allowScreenshotsFromWebPage) {
        String mobileAppExtras = "";
        if (allowJavaScriptControl) {
            mobileAppExtras = """
                        , hide: function() {
                          try {
                            window.AndroidInterface.hide();
                          } catch(e) {
                            console.error('Error in mobileApp.hide:', e);
                          }
                        },
                        show: function() {
                          try {
                            window.AndroidInterface.show();
                          } catch(e) {
                            console.error('Error in mobileApp.show:', e);
                          }
                        }
                """;
        }

        String screenshotBridge = allowScreenshotsFromWebPage
            ? """
                  ,
                  takeScreenshot: function() {
                    return new Promise(function(resolve, reject) {
                      try {
                            var screenshotBridge = window.AndroidInterface || nativeBridge;
                            if (!screenshotBridge || !screenshotBridge.takeScreenshot) {
                              reject(new Error('Screenshot bridge is not available'));
                              return;
                            }
                            var requestId = 'screenshot_' + Date.now() + '_' + Math.random().toString(36).slice(2);
                            window.__capgoInAppBrowserPendingScreenshots[requestId] = { resolve: resolve, reject: reject };
                            screenshotBridge.takeScreenshot(requestId);
                      } catch(e) {
                        reject(e);
                      }
                    });
                  }
              """
            : "";

        return String.format(
            """
            (function() {
              window.__capgoInAppBrowserPendingScreenshots = window.__capgoInAppBrowserPendingScreenshots || {};
              window.__capgoInAppBrowserResolveScreenshot = window.__capgoInAppBrowserResolveScreenshot || function(payload) {
                var pending = window.__capgoInAppBrowserPendingScreenshots[payload.requestId];
                if (!pending) {
                  return;
                }
                delete window.__capgoInAppBrowserPendingScreenshots[payload.requestId];
                pending.resolve(payload.result);
              };
              window.__capgoInAppBrowserRejectScreenshot = window.__capgoInAppBrowserRejectScreenshot || function(payload) {
                var pending = window.__capgoInAppBrowserPendingScreenshots[payload.requestId];
                if (!pending) {
                  return;
                }
                delete window.__capgoInAppBrowserPendingScreenshots[payload.requestId];
                pending.reject(new Error(payload.message));
              };
              var postMessageBridge = window.%s;
              var nativeBridge = window.AndroidInterface || window.mobileApp;
              if (postMessageBridge || nativeBridge) {
                window.mobileApp = {
                  postMessage: function(message) {
                    try {
                      var msg = typeof message === 'string' ? message : JSON.stringify(message);
                      if (typeof msg === 'undefined') {
                        msg = String(message);
                      }
                      if (postMessageBridge && postMessageBridge.postMessage) {
                        postMessageBridge.postMessage(msg);
                        return;
                      }
                      var fallbackBridge = window.AndroidInterface || nativeBridge;
                      if (fallbackBridge && fallbackBridge.postMessage) {
                        fallbackBridge.postMessage(msg);
                      }
                    } catch(e) {
                      console.error('Error in mobileApp.postMessage:', e);
                    }
                  },
                  close: function() {
                    try {
                      var closeBridge = window.AndroidInterface || nativeBridge;
                      if (closeBridge && closeBridge.close) {
                        closeBridge.close();
                      }
                    } catch(e) {
                      console.error('Error in mobileApp.close:', e);
                    }
                  }%s%s
                };
              }
              if (window.PrintInterface) {
                window.print = function() {
                  try {
                    window.PrintInterface.print();
                  } catch(e) {
                    console.error('Error in print:', e);
                  }
                };
              }
            })();
            """,
            POST_MESSAGE_BRIDGE_NAME,
            mobileAppExtras,
            screenshotBridge
        );
    }
}
