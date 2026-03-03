# @capgo/inappbrowser
 <a href="https://capgo.app/"><img src='https://raw.githubusercontent.com/Cap-go/capgo/main/assets/capgo_banner.png' alt='Capgo - Instant updates for capacitor'/></a>

<div align="center">
  <h2><a href="https://capgo.app/?ref=plugin_inappbrowser"> ➡️ Get Instant updates for your App with Capgo</a></h2>
  <h2><a href="https://capgo.app/consulting/?ref=plugin_inappbrowser"> Missing a feature? We’ll build the plugin for you 💪</a></h2>
</div>

Capacitor plugin in app browser with urlChangeEvent, two way communication, camera and microphone usage, etc.

## Why InAppBrowser?

The official Capacitor Browser plugin has strict security limitations that prevent advanced features. InAppBrowser removes these restrictions, enabling:

- **Two-way communication** between your app and the browser
- **JavaScript injection** for dynamic content manipulation
- **Camera and microphone access** within the browser context
- **URL change monitoring** for navigation tracking
- **Custom toolbars and UI** for branded experiences
- **Cookie and cache management** for session control
- **Custom sizes** for extra control of the display position

Perfect for OAuth flows, embedded web apps, video calls, and any scenario requiring deep integration with web content.

## Documentation

The most complete doc is available here: https://capgo.app/docs/plugins/inappbrowser/

## Compatibility

| Plugin version | Capacitor compatibility | Maintained |
| -------------- | ----------------------- | ---------- |
| v8.\*.\*       | v8.\*.\*                | ✅          |
| v7.\*.\*       | v7.\*.\*                | On demand   |
| v6.\*.\*       | v6.\*.\*                | ❌          |
| v5.\*.\*       | v5.\*.\*                | ❌          |

> **Note:** The major version of this plugin follows the major version of Capacitor. Use the version that matches your Capacitor installation (e.g., plugin v8 for Capacitor 8). Only the latest major version is actively maintained.

## Install

```bash
npm install @capgo/inappbrowser
npx cap sync
```
## Usage

```js
import { InAppBrowser } from '@capgo/inappbrowser'

InAppBrowser.open({ url: "YOUR_URL" });
```

### Customize Chrome Custom Tab Appearance (Android)

The `open()` method launches a Chrome Custom Tab on Android. You can customize its appearance to blend with your app:

```js
import { InAppBrowser } from '@capgo/inappbrowser'

InAppBrowser.open({
  url: "https://example.com",
  toolbarColor: "#1A1A2E",      // Match your app's theme
  showTitle: true,               // Show page title instead of raw URL
  showArrow: true,               // Back arrow instead of X close icon
  urlBarHidingEnabled: true,     // Auto-hide URL bar on scroll
  disableShare: true,            // Remove share from overflow menu
  disableBookmark: true,         // Hide bookmark icon (undocumented, may break)
  disableDownload: true,         // Hide download icon (undocumented, may break)
});
```

All CCT options are Android-only and safely ignored on iOS. See [`OpenOptions`](#openoptions) for full documentation.

### Open WebView with Custom Dimensions

By default, the webview opens in fullscreen. You can set custom dimensions to control the size and position:

```js
import { InAppBrowser } from '@capgo/inappbrowser'

// Open with custom dimensions (400x600 at position 50,100)
const { id } = await InAppBrowser.openWebView({
  url: "YOUR_URL",
  width: 400,
  height: 600,
  x: 50,
  y: 100
});

// Update dimensions at runtime
InAppBrowser.updateDimensions({
  id, // Optional, if omitted targets the active webview
  width: 500,
  height: 700,
  x: 100,
  y: 150
});
```

**Touch Passthrough**: When custom dimensions are set (not fullscreen), touches outside the webview bounds will pass through to the underlying Capacitor webview, allowing the user to interact with your app in the exposed areas. This enables picture-in-picture style experiences where the InAppBrowser floats above your content.

### Open WebView with Safe Margin

To create a webView with a 20px bottom margin (safe margin area outside the browser):

```js
import { InAppBrowser } from '@capgo/inappbrowser'

InAppBrowser.openWebView({
  url: "YOUR_URL",
  enabledSafeBottomMargin: true
});
```

Web platform is not supported. Use `window.open` instead.

### Open WebView in Full Screen Mode

To open the webview in true full screen mode (content extends behind the status bar), set `enabledSafeTopMargin` to `false`:

```js
import { InAppBrowser } from '@capgo/inappbrowser'

InAppBrowser.openWebView({
  url: "YOUR_URL",
  enabledSafeTopMargin: false  // Disables safe area at top, allows full screen
});
```

This option works independently of the toolbar type:
- **iOS**: The webview extends behind the status bar, providing true edge-to-edge content
- **Android**: The top margin is disabled, allowing content to fill the entire screen

Perfect for immersive experiences like video players, games, or full-screen web applications. Can be combined with any `toolbarType` setting.

### Test app and code:

https://github.com/Cap-go/demo-app/blob/main/src/views/plugins/Web.vue

### Camera usage

#### Android

Add the following to your `AndroidManifest.xml` file:

```xml
    <uses-permission android:name="android.permission.CAMERA" />
		<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
		<uses-permission android:name="android.permission.RECORD_AUDIO"/>
```

Then the permission will be asked when the camera is used.

#### iOS

Add the following to your `Info.plist` file:

```xml
<key>NSCameraUsageDescription</key>
<string>We need access to the camera to record audio.</string>
```

### Microphone usage

#### Android

Add the following to your `AndroidManifest.xml` file:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

Then the permission will be asked when the microphone is used.

#### iOS

Add the following to your `Info.plist` file:

```xml
<key>NSMicrophoneUsageDescription</key>
<string>We need access to the microphone to record audio.</string>
```

### Location usage

#### Android

Add the following to your `AndroidManifest.xml` file:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

Then the permission will be asked when location is requested by a website in the webview.

#### iOS

Add the following to your `Info.plist` file:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>We need access to your location to provide location-based services.</string>
```

### Two way communication

With this plugin you can send events from the main app to the inappbrowser and vice versa.

> The data is sent as a JSON object, so no functions or other non-JSON-serializable types are allowed.

#### Main app to inappbrowser, detail object is mendatory

```js
const { id } = await InAppBrowser.openWebView({ url: "YOUR_URL" });
InAppBrowser.postMessage({ id, detail: { message: "myMessage" } });
// Or broadcast to all open webviews
InAppBrowser.postMessage({ detail: { message: "broadcast" } });
```

#### Receive event from native in the inappbrowser

```js
window.addEventListener("messageFromNative", (event) => {
  console.log(event);
});
```

#### Send event from inappbrowser to main app, detail object is mendatory

```js
window.mobileApp.postMessage({ detail: { message: "myMessage" } });
```

#### Receive event from inappbrowser in the main app

```js
InAppBrowser.addListener("messageFromWebview", (event) => {
  console.log(event.id, event.detail);
});
```

### Close inappbrowser from inappbrowser itself

```js
window.mobileApp.close();
```

## API

<docgen-index>

* [`goBack(...)`](#goback)
* [`open(...)`](#open)
* [`clearCookies(...)`](#clearcookies)
* [`clearAllCookies(...)`](#clearallcookies)
* [`clearCache(...)`](#clearcache)
* [`getCookies(...)`](#getcookies)
* [`close(...)`](#close)
* [`hide()`](#hide)
* [`show()`](#show)
* [`openWebView(...)`](#openwebview)
* [`executeScript(...)`](#executescript)
* [`postMessage(...)`](#postmessage)
* [`setUrl(...)`](#seturl)
* [`addListener('urlChangeEvent', ...)`](#addlistenerurlchangeevent-)
* [`addListener('buttonNearDoneClick', ...)`](#addlistenerbuttonneardoneclick-)
* [`addListener('closeEvent', ...)`](#addlistenercloseevent-)
* [`addListener('confirmBtnClicked', ...)`](#addlistenerconfirmbtnclicked-)
* [`addListener('messageFromWebview', ...)`](#addlistenermessagefromwebview-)
* [`addListener('browserPageLoaded', ...)`](#addlistenerbrowserpageloaded-)
* [`addListener('pageLoadError', ...)`](#addlistenerpageloaderror-)
* [`removeAllListeners()`](#removealllisteners)
* [`reload(...)`](#reload)
* [`updateDimensions(...)`](#updatedimensions)
* [`openSecureWindow(...)`](#opensecurewindow)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)
* [Enums](#enums)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### goBack(...)

```typescript
goBack(options?: { id?: string | undefined; } | undefined) => Promise<{ canGoBack: boolean; }>
```

Navigates back in the WebView's history if possible

| Param         | Type                          |
| ------------- | ----------------------------- |
| **`options`** | <code>{ id?: string; }</code> |

**Returns:** <code>Promise&lt;{ canGoBack: boolean; }&gt;</code>

**Since:** 7.21.0

--------------------


### open(...)

```typescript
open(options: OpenOptions) => Promise<any>
```

Open url in a new window fullscreen, on android it use chrome custom tabs, on ios it use SFSafariViewController

| Param         | Type                                                |
| ------------- | --------------------------------------------------- |
| **`options`** | <code><a href="#openoptions">OpenOptions</a></code> |

**Returns:** <code>Promise&lt;any&gt;</code>

**Since:** 0.1.0

--------------------


### clearCookies(...)

```typescript
clearCookies(options: ClearCookieOptions) => Promise<any>
```

Clear cookies of url
When `id` is omitted, applies to all open webviews.

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#clearcookieoptions">ClearCookieOptions</a></code> |

**Returns:** <code>Promise&lt;any&gt;</code>

**Since:** 0.5.0

--------------------


### clearAllCookies(...)

```typescript
clearAllCookies(options?: { id?: string | undefined; } | undefined) => Promise<any>
```

Clear all cookies
When `id` is omitted, applies to all open webviews.

| Param         | Type                          |
| ------------- | ----------------------------- |
| **`options`** | <code>{ id?: string; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

**Since:** 6.5.0

--------------------


### clearCache(...)

```typescript
clearCache(options?: { id?: string | undefined; } | undefined) => Promise<any>
```

Clear cache
When `id` is omitted, applies to all open webviews.

| Param         | Type                          |
| ------------- | ----------------------------- |
| **`options`** | <code>{ id?: string; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

**Since:** 6.5.0

--------------------


### getCookies(...)

```typescript
getCookies(options: GetCookieOptions) => Promise<Record<string, string>>
```

Get cookies for a specific URL.

| Param         | Type                                                          | Description                                        |
| ------------- | ------------------------------------------------------------- | -------------------------------------------------- |
| **`options`** | <code><a href="#getcookieoptions">GetCookieOptions</a></code> | The options, including the URL to get cookies for. |

**Returns:** <code>Promise&lt;<a href="#record">Record</a>&lt;string, string&gt;&gt;</code>

--------------------


### close(...)

```typescript
close(options?: CloseWebviewOptions | undefined) => Promise<any>
```

Close the webview.
When `id` is omitted, closes the active webview.

| Param         | Type                                                                |
| ------------- | ------------------------------------------------------------------- |
| **`options`** | <code><a href="#closewebviewoptions">CloseWebviewOptions</a></code> |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### hide()

```typescript
hide() => Promise<void>
```

Hide the webview without closing it.
Use show() to bring it back.

**Since:** 8.0.8

--------------------


### show()

```typescript
show() => Promise<void>
```

Show a previously hidden webview.

**Since:** 8.0.8

--------------------


### openWebView(...)

```typescript
openWebView(options: OpenWebViewOptions) => Promise<{ id: string; }>
```

Open url in a new webview with toolbars, and enhanced capabilities, like camera access, file access, listen events, inject javascript, bi directional communication, etc.

JavaScript Interface:
When you open a webview with this method, a JavaScript interface is automatically injected that provides:
- `window.mobileApp.close()`: Closes the webview from JavaScript
- `window.mobileApp.postMessage({detail: {message: "myMessage"}})`: Sends a message from the webview to the app, detail object is the data you want to send to the webview

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#openwebviewoptions">OpenWebViewOptions</a></code> |

**Returns:** <code>Promise&lt;{ id: string; }&gt;</code>

**Since:** 0.1.0

--------------------


### executeScript(...)

```typescript
executeScript(options: { code: string; id?: string; }) => Promise<void>
```

Injects JavaScript code into the InAppBrowser window.
When `id` is omitted, executes in all open webviews.

| Param         | Type                                        |
| ------------- | ------------------------------------------- |
| **`options`** | <code>{ code: string; id?: string; }</code> |

--------------------


### postMessage(...)

```typescript
postMessage(options: { detail: Record<string, any>; id?: string; }) => Promise<void>
```

Sends an event to the webview(inappbrowser). you can listen to this event in the inappbrowser JS with window.addEventListener("messageFromNative", listenerFunc: (event: <a href="#record">Record</a>&lt;string, any&gt;) =&gt; void)
detail is the data you want to send to the webview, it's a requirement of Capacitor we cannot send direct objects
Your object has to be serializable to JSON, so no functions or other non-JSON-serializable types are allowed.
When `id` is omitted, broadcasts to all open webviews.

| Param         | Type                                                                                   |
| ------------- | -------------------------------------------------------------------------------------- |
| **`options`** | <code>{ detail: <a href="#record">Record</a>&lt;string, any&gt;; id?: string; }</code> |

--------------------


### setUrl(...)

```typescript
setUrl(options: { url: string; id?: string; }) => Promise<any>
```

Sets the URL of the webview.
When `id` is omitted, targets the active webview.

| Param         | Type                                       |
| ------------- | ------------------------------------------ |
| **`options`** | <code>{ url: string; id?: string; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### addListener('urlChangeEvent', ...)

```typescript
addListener(eventName: 'urlChangeEvent', listenerFunc: UrlChangeListener) => Promise<PluginListenerHandle>
```

Listen for url change, only for openWebView

| Param              | Type                                                            |
| ------------------ | --------------------------------------------------------------- |
| **`eventName`**    | <code>'urlChangeEvent'</code>                                   |
| **`listenerFunc`** | <code><a href="#urlchangelistener">UrlChangeListener</a></code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

**Since:** 0.0.1

--------------------


### addListener('buttonNearDoneClick', ...)

```typescript
addListener(eventName: 'buttonNearDoneClick', listenerFunc: ButtonNearListener) => Promise<PluginListenerHandle>
```

| Param              | Type                                                              |
| ------------------ | ----------------------------------------------------------------- |
| **`eventName`**    | <code>'buttonNearDoneClick'</code>                                |
| **`listenerFunc`** | <code><a href="#buttonnearlistener">ButtonNearListener</a></code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('closeEvent', ...)

```typescript
addListener(eventName: 'closeEvent', listenerFunc: UrlChangeListener) => Promise<PluginListenerHandle>
```

Listen for close click only for openWebView

| Param              | Type                                                            |
| ------------------ | --------------------------------------------------------------- |
| **`eventName`**    | <code>'closeEvent'</code>                                       |
| **`listenerFunc`** | <code><a href="#urlchangelistener">UrlChangeListener</a></code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

**Since:** 0.4.0

--------------------


### addListener('confirmBtnClicked', ...)

```typescript
addListener(eventName: 'confirmBtnClicked', listenerFunc: ConfirmBtnListener) => Promise<PluginListenerHandle>
```

Will be triggered when user clicks on confirm button when disclaimer is required,
works with openWebView shareDisclaimer and closeModal

| Param              | Type                                                              |
| ------------------ | ----------------------------------------------------------------- |
| **`eventName`**    | <code>'confirmBtnClicked'</code>                                  |
| **`listenerFunc`** | <code><a href="#confirmbtnlistener">ConfirmBtnListener</a></code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

**Since:** 0.0.1

--------------------


### addListener('messageFromWebview', ...)

```typescript
addListener(eventName: 'messageFromWebview', listenerFunc: (event: { id?: string; detail?: Record<string, any>; rawMessage?: string; }) => void) => Promise<PluginListenerHandle>
```

Will be triggered when event is sent from webview(inappbrowser), to send an event to the main app use window.mobileApp.postMessage({ "detail": { "message": "myMessage" } })
detail is the data you want to send to the main app, it's a requirement of Capacitor we cannot send direct objects
Your object has to be serializable to JSON, no functions or other non-JSON-serializable types are allowed.

This method is inject at runtime in the webview

| Param              | Type                                                                                                                             |
| ------------------ | -------------------------------------------------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'messageFromWebview'</code>                                                                                                |
| **`listenerFunc`** | <code>(event: { id?: string; detail?: <a href="#record">Record</a>&lt;string, any&gt;; rawMessage?: string; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('browserPageLoaded', ...)

```typescript
addListener(eventName: 'browserPageLoaded', listenerFunc: (event: { id?: string; }) => void) => Promise<PluginListenerHandle>
```

Will be triggered when page is loaded

| Param              | Type                                              |
| ------------------ | ------------------------------------------------- |
| **`eventName`**    | <code>'browserPageLoaded'</code>                  |
| **`listenerFunc`** | <code>(event: { id?: string; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('pageLoadError', ...)

```typescript
addListener(eventName: 'pageLoadError', listenerFunc: (event: { id?: string; }) => void) => Promise<PluginListenerHandle>
```

Will be triggered when page load error

| Param              | Type                                              |
| ------------------ | ------------------------------------------------- |
| **`eventName`**    | <code>'pageLoadError'</code>                      |
| **`listenerFunc`** | <code>(event: { id?: string; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

Remove all listeners for this plugin.

**Since:** 1.0.0

--------------------


### reload(...)

```typescript
reload(options?: { id?: string | undefined; } | undefined) => Promise<any>
```

Reload the current web page.

| Param         | Type                          |
| ------------- | ----------------------------- |
| **`options`** | <code>{ id?: string; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

**Since:** 1.0.0

--------------------


### updateDimensions(...)

```typescript
updateDimensions(options: DimensionOptions & { id?: string; }) => Promise<void>
```

Update the dimensions of the webview.
Allows changing the size and position of the webview at runtime.
When `id` is omitted, targets the active webview.

| Param         | Type                                                                             | Description                             |
| ------------- | -------------------------------------------------------------------------------- | --------------------------------------- |
| **`options`** | <code><a href="#dimensionoptions">DimensionOptions</a> & { id?: string; }</code> | Dimension options (width, height, x, y) |

--------------------


### openSecureWindow(...)

```typescript
openSecureWindow(options: OpenSecureWindowOptions) => Promise<OpenSecureWindowResponse>
```

Opens a secured window for OAuth2 authentication.
For web, you should have the code in the redirected page to use a broadcast channel to send the redirected url to the app
Something like:
```html
&lt;html&gt;
&lt;head&gt;&lt;/head&gt;
&lt;body&gt;
&lt;script&gt;
  const searchParams = new URLSearchParams(location.search)
  if (searchParams.has("code")) {
    new BroadcastChannel("my-channel-name").postMessage(location.href);
    window.close();
  }
&lt;/script&gt;
&lt;/body&gt;
&lt;/html&gt;
```
For mobile, you should have a redirect uri that opens the app, something like: `myapp://oauth_callback/`
And make sure to register it in the app's info.plist:
```xml
&lt;key&gt;CFBundleURLTypes&lt;/key&gt;
&lt;array&gt;
   &lt;dict&gt;
      &lt;key&gt;CFBundleURLSchemes&lt;/key&gt;
      &lt;array&gt;
         &lt;string&gt;myapp&lt;/string&gt;
      &lt;/array&gt;
   &lt;/dict&gt;
&lt;/array&gt;
```
And in the AndroidManifest.xml file:
```xml
&lt;activity&gt;
   &lt;intent-filter&gt;
      &lt;action android:name="android.intent.action.VIEW" /&gt;
      &lt;category android:name="android.intent.category.DEFAULT" /&gt;
      &lt;category android:name="android.intent.category.BROWSABLE" /&gt;
      &lt;data android:host="oauth_callback" android:scheme="myapp" /&gt;
   &lt;/intent-filter&gt;
&lt;/activity&gt;
```

| Param         | Type                                                                        | Description                                 |
| ------------- | --------------------------------------------------------------------------- | ------------------------------------------- |
| **`options`** | <code><a href="#opensecurewindowoptions">OpenSecureWindowOptions</a></code> | - the options for the openSecureWindow call |

**Returns:** <code>Promise&lt;<a href="#opensecurewindowresponse">OpenSecureWindowResponse</a>&gt;</code>

--------------------


### Interfaces


#### OpenOptions

| Prop                         | Type                 | Description                                                                                                                                                                             | Default            | Since |
| ---------------------------- | -------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------ | ----- |
| **`url`**                    | <code>string</code>  | Target URL to load.                                                                                                                                                                     |                    | 0.1.0 |
| **`isPresentAfterPageLoad`** | <code>boolean</code> | if true, the browser will be presented after the page is loaded, if false, the browser will be presented immediately.                                                                   |                    | 0.1.0 |
| **`preventDeeplink`**        | <code>boolean</code> | if true the deeplink will not be opened, if false the deeplink will be opened when clicked on the link                                                                                  |                    | 0.1.0 |
| **`toolbarColor`**           | <code>string</code>  | Toolbar background color in hex format (e.g., "#1A1A2E"). Applied to both light and dark color schemes. Also sets the navigation bar color to match. **Android only** — ignored on iOS. |                    | 8.2.0 |
| **`urlBarHidingEnabled`**    | <code>boolean</code> | Whether the URL bar should auto-hide when the user scrolls down. The bar reappears on any upward scroll. **Android only** — ignored on iOS.                                             | <code>false</code> | 8.2.0 |
| **`showTitle`**              | <code>boolean</code> | Show the page's HTML &lt;title&gt; in the toolbar instead of the raw URL. The true URL is still visible when the user taps the title area. **Android only** — ignored on iOS.           | <code>false</code> | 8.2.0 |
| **`showArrow`**              | <code>boolean</code> | Replace the default "X" close icon with a back arrow. Makes the Custom Tab feel like a native navigation push rather than a modal overlay. **Android only** — ignored on iOS.           | <code>false</code> | 8.2.0 |
| **`disableShare`**           | <code>boolean</code> | Remove the share action from the overflow menu. **Android only** — ignored on iOS.                                                                                                      | <code>false</code> | 8.2.0 |
| **`disableBookmark`**        | <code>boolean</code> | Hide the bookmark star icon in the overflow menu. Uses an undocumented Chromium intent extra — may stop working on future Chrome updates. **Android only** — ignored on iOS.            | <code>false</code> | 8.2.0 |
| **`disableDownload`**        | <code>boolean</code> | Hide the download icon in the overflow menu. Uses an undocumented Chromium intent extra — may stop working on future Chrome updates. **Android only** — ignored on iOS.                 | <code>false</code> | 8.2.0 |


#### ClearCookieOptions

| Prop      | Type                | Description                                                    |
| --------- | ------------------- | -------------------------------------------------------------- |
| **`id`**  | <code>string</code> | Target webview id. When omitted, applies to all open webviews. |
| **`url`** | <code>string</code> |                                                                |


#### HttpCookie

| Prop        | Type                | Description              |
| ----------- | ------------------- | ------------------------ |
| **`url`**   | <code>string</code> | The URL of the cookie.   |
| **`key`**   | <code>string</code> | The key of the cookie.   |
| **`value`** | <code>string</code> | The value of the cookie. |


#### GetCookieOptions

| Prop                  | Type                 |
| --------------------- | -------------------- |
| **`url`**             | <code>string</code>  |
| **`includeHttpOnly`** | <code>boolean</code> |


#### CloseWebviewOptions

| Prop             | Type                 | Description                                                        | Default           |
| ---------------- | -------------------- | ------------------------------------------------------------------ | ----------------- |
| **`id`**         | <code>string</code>  | Target webview id to close. If omitted, closes the active webview. |                   |
| **`isAnimated`** | <code>boolean</code> | Whether the webview closing is animated or not, ios only           | <code>true</code> |


#### OpenWebViewOptions

| Prop                                   | Type                                                                                                                                                                   | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | Default                                                       | Since  |
| -------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- | ------ |
| **`url`**                              | <code>string</code>                                                                                                                                                    | Target URL to load.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |                                                               | 0.1.0  |
| **`headers`**                          | <code><a href="#headers">Headers</a></code>                                                                                                                            | <a href="#headers">Headers</a> to send with the request.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |                                                               | 0.1.0  |
| **`credentials`**                      | <code><a href="#credentials">Credentials</a></code>                                                                                                                    | <a href="#credentials">Credentials</a> to send with the request and all subsequent requests for the same host.                                                                                                                                                                                                                                                                                                                                                                                                                                             |                                                               | 6.1.0  |
| **`method`**                           | <code>string</code>                                                                                                                                                    | HTTP method to use for the initial request. **Optional parameter - defaults to GET if not specified.** Existing code that doesn't provide this parameter will continue to work unchanged with standard GET requests. When specified with 'POST', 'PUT', or 'PATCH' methods that support a body, you can also provide a `body` parameter with the request payload. **Platform Notes:** - iOS: Full support for all HTTP methods with headers - Android: Custom headers may not be sent with POST/PUT/PATCH requests due to WebView limitations              | <code>"GET"</code>                                            | 8.2.0  |
| **`body`**                             | <code>string</code>                                                                                                                                                    | HTTP body to send with the request when using POST, PUT, or other methods that support a body. Should be a string (use JSON.stringify for JSON data). **Optional parameter - only used when `method` is specified and supports a request body.** Omitting this parameter (or using GET method) results in standard behavior without a request body.                                                                                                                                                                                                        |                                                               | 8.2.0  |
| **`materialPicker`**                   | <code>boolean</code>                                                                                                                                                   | materialPicker: if true, uses Material Design theme for date and time pickers on Android. This improves the appearance of HTML date inputs to use modern Material Design UI instead of the old style pickers.                                                                                                                                                                                                                                                                                                                                              | <code>false</code>                                            | 7.4.1  |
| **`jsInterface`**                      |                                                                                                                                                                        | JavaScript Interface: The webview automatically injects a JavaScript interface providing: - `window.mobileApp.close()`: Closes the webview from JavaScript - `window.mobileApp.postMessage(obj)`: Sends a message to the app (listen via "messageFromWebview" event) - `window.mobileApp.hide()` / `window.mobileApp.show()` when allowWebViewJsVisibilityControl is true in CapacitorConfig                                                                                                                                                               |                                                               | 6.10.0 |
| **`shareDisclaimer`**                  | <code><a href="#disclaimeroptions">DisclaimerOptions</a></code>                                                                                                        | Share options for the webview. When provided, shows a disclaimer dialog before sharing content. This is useful for: - Warning users about sharing sensitive information - Getting user consent before sharing - Explaining what will be shared - Complying with privacy regulations Note: shareSubject is required when using shareDisclaimer                                                                                                                                                                                                              |                                                               | 0.1.0  |
| **`toolbarType`**                      | <code><a href="#toolbartype">ToolBarType</a></code>                                                                                                                    | Toolbar type determines the appearance and behavior of the browser's toolbar - "activity": Shows a simple toolbar with just a close button and share button - "navigation": Shows a full navigation toolbar with back/forward buttons - "blank": Shows no toolbar - "": Default toolbar with close button                                                                                                                                                                                                                                                  | <code>ToolBarType.DEFAULT</code>                              | 0.1.0  |
| **`shareSubject`**                     | <code>string</code>                                                                                                                                                    | Subject text for sharing. Required when using shareDisclaimer. This text will be used as the subject line when sharing content.                                                                                                                                                                                                                                                                                                                                                                                                                            |                                                               | 0.1.0  |
| **`title`**                            | <code>string</code>                                                                                                                                                    | Title of the browser                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | <code>"New Window"</code>                                     | 0.1.0  |
| **`backgroundColor`**                  | <code><a href="#backgroundcolor">BackgroundColor</a></code>                                                                                                            | Background color of the browser                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | <code>BackgroundColor.BLACK</code>                            | 0.1.0  |
| **`activeNativeNavigationForWebview`** | <code>boolean</code>                                                                                                                                                   | If true, enables native navigation gestures within the webview. - Android: Native back button navigates within webview history - iOS: Enables swipe left/right gestures for back/forward navigation                                                                                                                                                                                                                                                                                                                                                        | <code>false (Android), true (iOS - enabled by default)</code> |        |
| **`disableGoBackOnNativeApplication`** | <code>boolean</code>                                                                                                                                                   | Disable the possibility to go back on native application, useful to force user to stay on the webview, Android only                                                                                                                                                                                                                                                                                                                                                                                                                                        | <code>false</code>                                            |        |
| **`isPresentAfterPageLoad`**           | <code>boolean</code>                                                                                                                                                   | Open url in a new window fullscreen isPresentAfterPageLoad: if true, the browser will be presented after the page is loaded, if false, the browser will be presented immediately.                                                                                                                                                                                                                                                                                                                                                                          | <code>false</code>                                            | 0.1.0  |
| **`isInspectable`**                    | <code>boolean</code>                                                                                                                                                   | Whether the website in the webview is inspectable or not, ios only                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | <code>false</code>                                            |        |
| **`isAnimated`**                       | <code>boolean</code>                                                                                                                                                   | Whether the webview opening is animated or not, ios only                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | <code>true</code>                                             |        |
| **`showReloadButton`**                 | <code>boolean</code>                                                                                                                                                   | Shows a reload button that reloads the web page                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | <code>false</code>                                            | 1.0.15 |
| **`closeModal`**                       | <code>boolean</code>                                                                                                                                                   | CloseModal: if true a confirm will be displayed when user clicks on close button, if false the browser will be closed immediately.                                                                                                                                                                                                                                                                                                                                                                                                                         | <code>false</code>                                            | 1.1.0  |
| **`closeModalTitle`**                  | <code>string</code>                                                                                                                                                    | CloseModalTitle: title of the confirm when user clicks on close button                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | <code>"Close"</code>                                          | 1.1.0  |
| **`closeModalDescription`**            | <code>string</code>                                                                                                                                                    | CloseModalDescription: description of the confirm when user clicks on close button                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | <code>"Are you sure you want to close this window?"</code>    | 1.1.0  |
| **`closeModalOk`**                     | <code>string</code>                                                                                                                                                    | CloseModalOk: text of the confirm button when user clicks on close button                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | <code>"Close"</code>                                          | 1.1.0  |
| **`closeModalCancel`**                 | <code>string</code>                                                                                                                                                    | CloseModalCancel: text of the cancel button when user clicks on close button                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | <code>"Cancel"</code>                                         | 1.1.0  |
| **`visibleTitle`**                     | <code>boolean</code>                                                                                                                                                   | visibleTitle: if true the website title would be shown else shown empty                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | <code>true</code>                                             | 1.2.5  |
| **`toolbarColor`**                     | <code>string</code>                                                                                                                                                    | toolbarColor: color of the toolbar in hex format                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | <code>"#ffffff"</code>                                        | 1.2.5  |
| **`toolbarTextColor`**                 | <code>string</code>                                                                                                                                                    | toolbarTextColor: color of the buttons and title in the toolbar in hex format When set, it overrides the automatic light/dark mode detection for text color                                                                                                                                                                                                                                                                                                                                                                                                | <code>calculated based on toolbarColor brightness</code>      | 6.10.0 |
| **`showArrow`**                        | <code>boolean</code>                                                                                                                                                   | showArrow: if true an arrow would be shown instead of cross for closing the window                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | <code>false</code>                                            | 1.2.5  |
| **`ignoreUntrustedSSLError`**          | <code>boolean</code>                                                                                                                                                   | ignoreUntrustedSSLError: if true, the webview will ignore untrusted SSL errors allowing the user to view the website.                                                                                                                                                                                                                                                                                                                                                                                                                                      | <code>false</code>                                            | 6.1.0  |
| **`preShowScript`**                    | <code>string</code>                                                                                                                                                    | preShowScript: if isPresentAfterPageLoad is true and this variable is set the plugin will inject a script before showing the browser. This script will be run in an async context. The plugin will wait for the script to finish (max 10 seconds)                                                                                                                                                                                                                                                                                                          |                                                               | 6.6.0  |
| **`preShowScriptInjectionTime`**       | <code>'documentStart' \| 'pageLoad'</code>                                                                                                                             | preShowScriptInjectionTime: controls when the preShowScript is injected. - "documentStart": injects before any page JavaScript runs (good for polyfills like Firebase) - "pageLoad": injects after page load (default, original behavior)                                                                                                                                                                                                                                                                                                                  | <code>"pageLoad"</code>                                       | 7.26.0 |
| **`proxyRequests`**                    | <code>string</code>                                                                                                                                                    | proxyRequests is a regex expression. Please see [this pr](https://github.com/Cap-go/capacitor-inappbrowser/pull/222) for more info. (Android only)                                                                                                                                                                                                                                                                                                                                                                                                         |                                                               | 6.9.0  |
| **`buttonNearDone`**                   | <code>{ ios: { iconType: 'sf-symbol' \| 'asset'; icon: string; }; android: { iconType: 'asset' \| 'vector'; icon: string; width?: number; height?: number; }; }</code> | buttonNearDone allows for a creation of a custom button near the done/close button. The button is only shown when toolbarType is not "activity", "navigation", or "blank". For Android: - iconType must be "asset" - icon path should be in the public folder (e.g. "monkey.svg") - width and height are optional, defaults to 48dp - button is positioned at the end of toolbar with 8dp margin For iOS: - iconType can be "sf-symbol" or "asset" - for sf-symbol, icon should be the symbol name - for asset, icon should be the asset name              |                                                               | 6.7.0  |
| **`textZoom`**                         | <code>number</code>                                                                                                                                                    | textZoom: sets the text zoom of the page in percent. Allows users to increase or decrease the text size for better readability.                                                                                                                                                                                                                                                                                                                                                                                                                            | <code>100</code>                                              | 7.6.0  |
| **`preventDeeplink`**                  | <code>boolean</code>                                                                                                                                                   | preventDeeplink: if true, the deeplink will not be opened, if false the deeplink will be opened when clicked on the link. on IOS each schema need to be added to info.plist file under LSApplicationQueriesSchemes when false to make it work.                                                                                                                                                                                                                                                                                                             | <code>false</code>                                            | 0.1.0  |
| **`authorizedAppLinks`**               | <code>string[]</code>                                                                                                                                                  | List of base URLs whose hosts are treated as authorized App Links (Android) and Universal Links (iOS). - On both platforms, only HTTPS links whose host matches any entry in this list will attempt to open via the corresponding native application. - If the app is not installed or the system cannot handle the link, the URL will continue loading inside the in-app browser. - Matching is host-based (case-insensitive), ignoring the "www." prefix. - When `preventDeeplink` is enabled, all external handling is blocked regardless of this list. | <code>[]</code>                                               | 7.12.0 |
| **`enabledSafeBottomMargin`**          | <code>boolean</code>                                                                                                                                                   | If true, the webView will not take the full height and will have a 20px margin at the bottom. This creates a safe margin area outside the browser view.                                                                                                                                                                                                                                                                                                                                                                                                    | <code>false</code>                                            | 7.13.0 |
| **`enabledSafeTopMargin`**             | <code>boolean</code>                                                                                                                                                   | If false, the webView will extend behind the status bar for true full-screen immersive content. When true (default), respects the safe area at the top of the screen. Works independently of toolbarType - use for full-screen video players, games, or immersive web apps.                                                                                                                                                                                                                                                                                | <code>true</code>                                             | 8.2.0  |
| **`useTopInset`**                      | <code>boolean</code>                                                                                                                                                   | When true, applies the system status bar inset as the WebView top margin on Android. Keeps the legacy 0px margin by default for apps that handle padding themselves.                                                                                                                                                                                                                                                                                                                                                                                       | <code>false</code>                                            |        |
| **`enableGooglePaySupport`**           | <code>boolean</code>                                                                                                                                                   | enableGooglePaySupport: if true, enables support for Google Pay popups and Payment Request API. This fixes OR_BIBED_15 errors by allowing popup windows and configuring Cross-Origin-Opener-Policy. Only enable this if you need Google Pay functionality as it allows popup windows. When enabled: - Allows popup windows for Google Pay authentication - Sets proper CORS headers for Payment Request API - Enables multiple window support in WebView - Configures secure context for payment processing                                                | <code>false</code>                                            | 7.13.0 |
| **`blockedHosts`**                     | <code>string[]</code>                                                                                                                                                  | blockedHosts: List of host patterns that should be blocked from loading in the InAppBrowser's internal navigations. Any request inside WebView to a URL with a host matching any of these patterns will be blocked. Supports wildcard patterns like: - "*.example.com" to block all subdomains - "www.example.*" to block wildcard domain extensions                                                                                                                                                                                                       | <code>[]</code>                                               | 7.17.0 |
| **`width`**                            | <code>number</code>                                                                                                                                                    | Width of the webview in pixels. If not set, webview will be fullscreen width.                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | <code>undefined (fullscreen)</code>                           |        |
| **`height`**                           | <code>number</code>                                                                                                                                                    | Height of the webview in pixels. If not set, webview will be fullscreen height.                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | <code>undefined (fullscreen)</code>                           |        |
| **`x`**                                | <code>number</code>                                                                                                                                                    | X position of the webview in pixels from the left edge. Only effective when width is set.                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | <code>0</code>                                                |        |
| **`y`**                                | <code>number</code>                                                                                                                                                    | Y position of the webview in pixels from the top edge. Only effective when height is set.                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | <code>0</code>                                                |        |
| **`disableOverscroll`**                | <code>boolean</code>                                                                                                                                                   | Disables the bounce (overscroll) effect on iOS WebView. When enabled, prevents the rubber band scrolling effect when users scroll beyond content boundaries. This is useful for: - Creating a more native, app-like experience - Preventing accidental overscroll states - Avoiding issues when keyboard opens/closes Note: This option only affects iOS. Android does not have this bounce effect by default.                                                                                                                                             | <code>false</code>                                            | 8.0.2  |
| **`hidden`**                           | <code>boolean</code>                                                                                                                                                   | Opens the webview in hidden mode (not visible to user but fully functional). When hidden, the webview loads and executes JavaScript but is not displayed. All control methods (executeScript, postMessage, setUrl, etc.) work while hidden. Use close() to clean up the hidden webview when done.                                                                                                                                                                                                                                                          | <code>false</code>                                            | 8.0.7  |
| **`invisibilityMode`**                 | <code><a href="#invisibilitymode">InvisibilityMode</a></code>                                                                                                          | Controls how a hidden webview reports its visibility and size. - AWARE: webview is aware it's hidden (dimensions may be zero). - FAKE_VISIBLE: webview is hidden but reports fullscreen dimensions (uses alpha=0 to remain invisible).                                                                                                                                                                                                                                                                                                                     | <code>InvisibilityMode.AWARE</code>                           |        |


#### Headers


#### Credentials

| Prop           | Type                |
| -------------- | ------------------- |
| **`username`** | <code>string</code> |
| **`password`** | <code>string</code> |


#### DisclaimerOptions

| Prop             | Type                | Description                            | Default                |
| ---------------- | ------------------- | -------------------------------------- | ---------------------- |
| **`title`**      | <code>string</code> | Title of the disclaimer dialog         | <code>"Title"</code>   |
| **`message`**    | <code>string</code> | Message shown in the disclaimer dialog | <code>"Message"</code> |
| **`confirmBtn`** | <code>string</code> | Text for the confirm button            | <code>"Confirm"</code> |
| **`cancelBtn`**  | <code>string</code> | Text for the cancel button             | <code>"Cancel"</code>  |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### UrlEvent

| Prop      | Type                | Description               | Since |
| --------- | ------------------- | ------------------------- | ----- |
| **`id`**  | <code>string</code> | Webview instance id.      |       |
| **`url`** | <code>string</code> | Emit when the url changes | 0.0.1 |


#### BtnEvent

| Prop      | Type                | Description                    | Since |
| --------- | ------------------- | ------------------------------ | ----- |
| **`id`**  | <code>string</code> | Webview instance id.           |       |
| **`url`** | <code>string</code> | Emit when a button is clicked. | 0.0.1 |


#### DimensionOptions

| Prop         | Type                | Description                             |
| ------------ | ------------------- | --------------------------------------- |
| **`width`**  | <code>number</code> | Width of the webview in pixels          |
| **`height`** | <code>number</code> | Height of the webview in pixels         |
| **`x`**      | <code>number</code> | X position from the left edge in pixels |
| **`y`**      | <code>number</code> | Y position from the top edge in pixels  |


#### OpenSecureWindowResponse

| Prop                | Type                | Description                             |
| ------------------- | ------------------- | --------------------------------------- |
| **`redirectedUri`** | <code>string</code> | The result of the openSecureWindow call |


#### OpenSecureWindowOptions

| Prop                       | Type                | Description                                                                                                                                                     |
| -------------------------- | ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`authEndpoint`**         | <code>string</code> | The endpoint to open                                                                                                                                            |
| **`redirectUri`**          | <code>string</code> | The redirect URI to use for the openSecureWindow call. This will be checked to make sure it matches the redirect URI after the window finishes the redirection. |
| **`broadcastChannelName`** | <code>string</code> | The name of the broadcast channel to listen to, relevant only for web                                                                                           |


### Type Aliases


#### ClearCookieOptions

<code><a href="#omit">Omit</a>&lt;<a href="#httpcookie">HttpCookie</a>, 'key' | 'value'&gt;</code>


#### Omit

Construct a type with the properties of T except for those in type K.

<code><a href="#pick">Pick</a>&lt;T, <a href="#exclude">Exclude</a>&lt;keyof T, K&gt;&gt;</code>


#### Pick

From T, pick a set of properties whose keys are in the union K

<code>{ [P in K]: T[P]; }</code>


#### Exclude

<a href="#exclude">Exclude</a> from T those types that are assignable to U

<code>T extends U ? never : T</code>


#### Record

Construct a type with a set of properties K of type T

<code>{ [P in K]: T; }</code>


#### GetCookieOptions

<code><a href="#omit">Omit</a>&lt;<a href="#httpcookie">HttpCookie</a>, 'key' | 'value'&gt;</code>


#### UrlChangeListener

<code>(state: <a href="#urlevent">UrlEvent</a>): void</code>


#### ButtonNearListener

<code>(state: object): void</code>


#### ConfirmBtnListener

<code>(state: <a href="#btnevent">BtnEvent</a>): void</code>


### Enums


#### ToolBarType

| Members          | Value                     | Description                                                      | Since |
| ---------------- | ------------------------- | ---------------------------------------------------------------- | ----- |
| **`ACTIVITY`**   | <code>'activity'</code>   | Shows a simple toolbar with just a close button and share button | 0.1.0 |
| **`COMPACT`**    | <code>'compact'</code>    | Shows a simple toolbar with just a close button                  | 7.6.8 |
| **`NAVIGATION`** | <code>'navigation'</code> | Shows a full navigation toolbar with back/forward buttons        | 0.1.0 |
| **`BLANK`**      | <code>'blank'</code>      | Shows no toolbar                                                 | 0.1.0 |


#### BackgroundColor

| Members     | Value                |
| ----------- | -------------------- |
| **`WHITE`** | <code>'white'</code> |
| **`BLACK`** | <code>'black'</code> |


#### InvisibilityMode

| Members            | Value                       | Description                                                                             |
| ------------------ | --------------------------- | --------------------------------------------------------------------------------------- |
| **`AWARE`**        | <code>'AWARE'</code>        | WebView is aware it is hidden (dimensions may be zero).                                 |
| **`FAKE_VISIBLE`** | <code>'FAKE_VISIBLE'</code> | WebView is hidden but reports fullscreen dimensions (uses alpha=0 to remain invisible). |

</docgen-api>

**Credits**
 - [WKWebViewController](https://github.com/Meniny/WKWebViewController) - for iOS
 - [CapBrowser](https://github.com/gadapa-rakesh/CapBrowser) - For the base in capacitor v2
