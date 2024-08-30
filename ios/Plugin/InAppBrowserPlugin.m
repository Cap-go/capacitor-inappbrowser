#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

// Define the plugin using the CAP_PLUGIN Macro, and
// each method the plugin supports using the CAP_PLUGIN_METHOD macro.
CAP_PLUGIN(InAppBrowserPlugin, "InAppBrowser",
           CAP_PLUGIN_METHOD(openWebView, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(clearCookies, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(getCookies, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(clearAllCookies, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(reload, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(open, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(setUrl, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(show, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(close, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(hide, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(executeScript, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(postMessage, CAPPluginReturnPromise);
           CAP_PLUGIN_METHOD(insertCSS, CAPPluginReturnPromise);
)
