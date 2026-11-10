# Consumer ProGuard rules for apps that depend on this library (R8 in release, Capacitor 9+).

# WebView.addJavascriptInterface resolves methods by name from classes such as WebViewDialog.JavaScriptInterface
# and ProxyBridge. R8 can remove or rename @JavascriptInterface methods without this keep.
# Keep annotation metadata Capacitor and WebView read through reflection.
-keepattributes RuntimeVisibleAnnotations

-keepclassmembers class ee.forgr.capacitor_inappbrowser.** {
    @android.webkit.JavascriptInterface <methods>;
}
