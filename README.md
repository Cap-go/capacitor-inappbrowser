# @capgo/inappbrowser
 <a href="https://capgo.app/"><img src='https://raw.githubusercontent.com/Cap-go/capgo/main/assets/capgo_banner.png' alt='Capgo - Instant updates for capacitor'/></a>

<div align="center">
  <h2><a href="https://capgo.app/?ref=plugin"> ➡️ Get Instant updates for your App with Capgo 🚀</a></h2>
  <h2><a href="https://capgo.app/consulting/?ref=plugin"> Fix your annoying bug now, Hire a Capacitor expert 💪</a></h2>
</div>

Capacitor plugin in app browser with urlChangeEvent, two way communication, camera and microphone usage, etc.

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

Web platform is not supported. Use `window.open` instead.


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

### Two way communication

With this plugin you can send events from the main app to the inappbrowser and vice versa.

> The data is sent as a JSON object, so no functions or other non-JSON-serializable types are allowed.

#### Main app to inappbrowser, detail object is mendatory

```js
InAppBrowser.postMessage({ detail: { message: "myMessage" } });
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
window.addEventListener("messageFromWebview", (event) => {
  console.log(event);
});
```

### Close inappbrowser from inappbrowser itself

```js
window.mobileApp.close();
```

## API

<docgen-index>

* [`open(...)`](#open)
* [`clearCookies(...)`](#clearcookies)
* [`clearAllCookies()`](#clearallcookies)
* [`clearCache()`](#clearcache)
* [`getCookies(...)`](#getcookies)
* [`close()`](#close)
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
* [`reload()`](#reload)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)
* [Enums](#enums)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

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

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#clearcookieoptions">ClearCookieOptions</a></code> |

**Returns:** <code>Promise&lt;any&gt;</code>

**Since:** 0.5.0

--------------------


### clearAllCookies()

```typescript
clearAllCookies() => Promise<any>
```

Clear all cookies

**Returns:** <code>Promise&lt;any&gt;</code>

**Since:** 6.5.0

--------------------


### clearCache()

```typescript
clearCache() => Promise<any>
```

Clear cache

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


### close()

```typescript
close() => Promise<any>
```

Close the webview.

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### openWebView(...)

```typescript
openWebView(options: OpenWebViewOptions) => Promise<any>
```

Open url in a new webview with toolbars, and enhanced capabilities, like camera access, file access, listen events, inject javascript, bi directional communication, etc.

JavaScript Interface:
When you open a webview with this method, a JavaScript interface is automatically injected that provides:
- `window.mobileApp.close()`: Closes the webview from JavaScript
- `window.mobileApp.postMessage({detail: {message: 'myMessage'}})`: Sends a message from the webview to the app, detail object is the data you want to send to the webview

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#openwebviewoptions">OpenWebViewOptions</a></code> |

**Returns:** <code>Promise&lt;any&gt;</code>

**Since:** 0.1.0

--------------------


### executeScript(...)

```typescript
executeScript({ code }: { code: string; }) => Promise<void>
```

Injects JavaScript code into the InAppBrowser window.

| Param     | Type                           |
| --------- | ------------------------------ |
| **`__0`** | <code>{ code: string; }</code> |

--------------------


### postMessage(...)

```typescript
postMessage(options: { detail: Record<string, any>; }) => Promise<void>
```

Sends an event to the webview(inappbrowser). you can listen to this event in the inappbrowser JS with window.addEventListener("messageFromNative", listenerFunc: (event: <a href="#record">Record</a>&lt;string, any&gt;) =&gt; void)
detail is the data you want to send to the webview, it's a requirement of Capacitor we cannot send direct objects
Your object has to be serializable to JSON, so no functions or other non-JSON-serializable types are allowed.

| Param         | Type                                                                      |
| ------------- | ------------------------------------------------------------------------- |
| **`options`** | <code>{ detail: <a href="#record">Record</a>&lt;string, any&gt;; }</code> |

--------------------


### setUrl(...)

```typescript
setUrl(options: { url: string; }) => Promise<any>
```

Sets the URL of the webview.

| Param         | Type                          |
| ------------- | ----------------------------- |
| **`options`** | <code>{ url: string; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### addListener('urlChangeEvent', ...)

```typescript
addListener(eventName: "urlChangeEvent", listenerFunc: UrlChangeListener) => Promise<PluginListenerHandle>
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
addListener(eventName: "buttonNearDoneClick", listenerFunc: ButtonNearListener) => Promise<PluginListenerHandle>
```

| Param              | Type                                                              |
| ------------------ | ----------------------------------------------------------------- |
| **`eventName`**    | <code>'buttonNearDoneClick'</code>                                |
| **`listenerFunc`** | <code><a href="#buttonnearlistener">ButtonNearListener</a></code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('closeEvent', ...)

```typescript
addListener(eventName: "closeEvent", listenerFunc: UrlChangeListener) => Promise<PluginListenerHandle>
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
addListener(eventName: "confirmBtnClicked", listenerFunc: ConfirmBtnListener) => Promise<PluginListenerHandle>
```

Will be triggered when user clicks on confirm button when disclaimer is required

| Param              | Type                                                              |
| ------------------ | ----------------------------------------------------------------- |
| **`eventName`**    | <code>'confirmBtnClicked'</code>                                  |
| **`listenerFunc`** | <code><a href="#confirmbtnlistener">ConfirmBtnListener</a></code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

**Since:** 0.0.1

--------------------


### addListener('messageFromWebview', ...)

```typescript
addListener(eventName: "messageFromWebview", listenerFunc: (event: { detail: Record<string, any>; }) => void) => Promise<PluginListenerHandle>
```

Will be triggered when event is sent from webview(inappbrowser), to send an event to the main app use window.mobileApp.postMessage({ "detail": { "message": "myMessage" } })
detail is the data you want to send to the main app, it's a requirement of Capacitor we cannot send direct objects
Your object has to be serializable to JSON, no functions or other non-JSON-serializable types are allowed.

This method is inject at runtime in the webview

| Param              | Type                                                                                          |
| ------------------ | --------------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'messageFromWebview'</code>                                                             |
| **`listenerFunc`** | <code>(event: { detail: <a href="#record">Record</a>&lt;string, any&gt;; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('browserPageLoaded', ...)

```typescript
addListener(eventName: "browserPageLoaded", listenerFunc: () => void) => Promise<PluginListenerHandle>
```

Will be triggered when page is loaded

| Param              | Type                             |
| ------------------ | -------------------------------- |
| **`eventName`**    | <code>'browserPageLoaded'</code> |
| **`listenerFunc`** | <code>() =&gt; void</code>       |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('pageLoadError', ...)

```typescript
addListener(eventName: "pageLoadError", listenerFunc: () => void) => Promise<PluginListenerHandle>
```

Will be triggered when page load error

| Param              | Type                         |
| ------------------ | ---------------------------- |
| **`eventName`**    | <code>'pageLoadError'</code> |
| **`listenerFunc`** | <code>() =&gt; void</code>   |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

Remove all listeners for this plugin.

**Since:** 1.0.0

--------------------


### reload()

```typescript
reload() => Promise<any>
```

Reload the current web page.

**Returns:** <code>Promise&lt;any&gt;</code>

**Since:** 1.0.0

--------------------


### Interfaces


#### OpenOptions

| Prop                         | Type                 | Description                                                                                                           | Since |
| ---------------------------- | -------------------- | --------------------------------------------------------------------------------------------------------------------- | ----- |
| **`url`**                    | <code>string</code>  | Target URL to load.                                                                                                   | 0.1.0 |
| **`isPresentAfterPageLoad`** | <code>boolean</code> | if true, the browser will be presented after the page is loaded, if false, the browser will be presented immediately. | 0.1.0 |
| **`preventDeeplink`**        | <code>boolean</code> | if true the deeplink will not be opened, if false the deeplink will be opened when clicked on the link                | 0.1.0 |


#### ClearCookieOptions

| Prop      | Type                |
| --------- | ------------------- |
| **`url`** | <code>string</code> |


#### HttpCookie

| Prop        | Type                |
| ----------- | ------------------- |
| **`url`**   | <code>string</code> |
| **`key`**   | <code>string</code> |
| **`value`** | <code>string</code> |


#### GetCookieOptions

| Prop                  | Type                 |
| --------------------- | -------------------- |
| **`url`**             | <code>string</code>  |
| **`includeHttpOnly`** | <code>boolean</code> |


#### OpenWebViewOptions

| Prop                                   | Type                                                                                                                                                                   | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | Default                                                    | Since  |
| -------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- | ------ |
| **`url`**                              | <code>string</code>                                                                                                                                                    | Target URL to load.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |                                                            | 0.1.0  |
| **`headers`**                          | <code><a href="#headers">Headers</a></code>                                                                                                                            | <a href="#headers">Headers</a> to send with the request.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |                                                            | 0.1.0  |
| **`credentials`**                      | <code><a href="#credentials">Credentials</a></code>                                                                                                                    | <a href="#credentials">Credentials</a> to send with the request and all subsequent requests for the same host.                                                                                                                                                                                                                                                                                                                                                                                                                                |                                                            | 6.1.0  |
| **`materialPicker`**                   | <code>boolean</code>                                                                                                                                                   | materialPicker: if true, uses Material Design theme for date and time pickers on Android. This improves the appearance of HTML date inputs to use modern Material Design UI instead of the old style pickers.                                                                                                                                                                                                                                                                                                                                 | <code>false</code>                                         | 7.4.1  |
| **`jsInterface`**                      |                                                                                                                                                                        | JavaScript Interface: The webview automatically injects a JavaScript interface providing: - `window.mobileApp.close()`: Closes the webview from JavaScript - `window.mobileApp.postMessage(obj)`: Sends a message to the app (listen via "messageFromWebview" event)                                                                                                                                                                                                                                                                          |                                                            | 6.10.0 |
| **`shareDisclaimer`**                  | <code><a href="#disclaimeroptions">DisclaimerOptions</a></code>                                                                                                        | Share options for the webview. When provided, shows a disclaimer dialog before sharing content. This is useful for: - Warning users about sharing sensitive information - Getting user consent before sharing - Explaining what will be shared - Complying with privacy regulations Note: shareSubject is required when using shareDisclaimer                                                                                                                                                                                                 |                                                            | 0.1.0  |
| **`toolbarType`**                      | <code><a href="#toolbartype">ToolBarType</a></code>                                                                                                                    | Toolbar type determines the appearance and behavior of the browser's toolbar - "activity": Shows a simple toolbar with just a close button and share button - "navigation": Shows a full navigation toolbar with back/forward buttons - "blank": Shows no toolbar - "": Default toolbar with close button                                                                                                                                                                                                                                     | <code>ToolBarType.DEFAULT</code>                           | 0.1.0  |
| **`shareSubject`**                     | <code>string</code>                                                                                                                                                    | Subject text for sharing. Required when using shareDisclaimer. This text will be used as the subject line when sharing content.                                                                                                                                                                                                                                                                                                                                                                                                               |                                                            | 0.1.0  |
| **`title`**                            | <code>string</code>                                                                                                                                                    | Title of the browser                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | <code>'New Window'</code>                                  | 0.1.0  |
| **`backgroundColor`**                  | <code><a href="#backgroundcolor">BackgroundColor</a></code>                                                                                                            | Background color of the browser                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | <code>BackgroundColor.BLACK</code>                         | 0.1.0  |
| **`activeNativeNavigationForWebview`** | <code>boolean</code>                                                                                                                                                   | If true, active the native navigation within the webview, Android only                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | <code>false</code>                                         |        |
| **`disableGoBackOnNativeApplication`** | <code>boolean</code>                                                                                                                                                   | Disable the possibility to go back on native application, useful to force user to stay on the webview, Android only                                                                                                                                                                                                                                                                                                                                                                                                                           | <code>false</code>                                         |        |
| **`isPresentAfterPageLoad`**           | <code>boolean</code>                                                                                                                                                   | Open url in a new window fullscreen isPresentAfterPageLoad: if true, the browser will be presented after the page is loaded, if false, the browser will be presented immediately.                                                                                                                                                                                                                                                                                                                                                             | <code>false</code>                                         | 0.1.0  |
| **`isInspectable`**                    | <code>boolean</code>                                                                                                                                                   | Whether the website in the webview is inspectable or not, ios only                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | <code>false</code>                                         |        |
| **`isAnimated`**                       | <code>boolean</code>                                                                                                                                                   | Whether the webview opening is animated or not, ios only                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | <code>true</code>                                          |        |
| **`showReloadButton`**                 | <code>boolean</code>                                                                                                                                                   | Shows a reload button that reloads the web page                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | <code>false</code>                                         | 1.0.15 |
| **`closeModal`**                       | <code>boolean</code>                                                                                                                                                   | CloseModal: if true a confirm will be displayed when user clicks on close button, if false the browser will be closed immediately.                                                                                                                                                                                                                                                                                                                                                                                                            | <code>false</code>                                         | 1.1.0  |
| **`closeModalTitle`**                  | <code>string</code>                                                                                                                                                    | CloseModalTitle: title of the confirm when user clicks on close button                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | <code>'Close'</code>                                       | 1.1.0  |
| **`closeModalDescription`**            | <code>string</code>                                                                                                                                                    | CloseModalDescription: description of the confirm when user clicks on close button                                                                                                                                                                                                                                                                                                                                                                                                                                                            | <code>'Are you sure you want to close this window?'</code> | 1.1.0  |
| **`closeModalOk`**                     | <code>string</code>                                                                                                                                                    | CloseModalOk: text of the confirm button when user clicks on close button                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | <code>'Close'</code>                                       | 1.1.0  |
| **`closeModalCancel`**                 | <code>string</code>                                                                                                                                                    | CloseModalCancel: text of the cancel button when user clicks on close button                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | <code>'Cancel'</code>                                      | 1.1.0  |
| **`visibleTitle`**                     | <code>boolean</code>                                                                                                                                                   | visibleTitle: if true the website title would be shown else shown empty                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | <code>true</code>                                          | 1.2.5  |
| **`toolbarColor`**                     | <code>string</code>                                                                                                                                                    | toolbarColor: color of the toolbar in hex format                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | <code>'#ffffff'</code>                                     | 1.2.5  |
| **`toolbarTextColor`**                 | <code>string</code>                                                                                                                                                    | toolbarTextColor: color of the buttons and title in the toolbar in hex format When set, it overrides the automatic light/dark mode detection for text color                                                                                                                                                                                                                                                                                                                                                                                   | <code>calculated based on toolbarColor brightness</code>   | 6.10.0 |
| **`showArrow`**                        | <code>boolean</code>                                                                                                                                                   | showArrow: if true an arrow would be shown instead of cross for closing the window                                                                                                                                                                                                                                                                                                                                                                                                                                                            | <code>false</code>                                         | 1.2.5  |
| **`ignoreUntrustedSSLError`**          | <code>boolean</code>                                                                                                                                                   | ignoreUntrustedSSLError: if true, the webview will ignore untrusted SSL errors allowing the user to view the website.                                                                                                                                                                                                                                                                                                                                                                                                                         | <code>false</code>                                         | 6.1.0  |
| **`preShowScript`**                    | <code>string</code>                                                                                                                                                    | preShowScript: if isPresentAfterPageLoad is true and this variable is set the plugin will inject a script before showing the browser. This script will be run in an async context. The plugin will wait for the script to finish (max 10 seconds)                                                                                                                                                                                                                                                                                             |                                                            | 6.6.0  |
| **`proxyRequests`**                    | <code>string</code>                                                                                                                                                    | proxyRequests is a regex expression. Please see [this pr](https://github.com/Cap-go/capacitor-inappbrowser/pull/222) for more info. (Android only)                                                                                                                                                                                                                                                                                                                                                                                            |                                                            | 6.9.0  |
| **`buttonNearDone`**                   | <code>{ ios: { iconType: 'sf-symbol' \| 'asset'; icon: string; }; android: { iconType: 'asset' \| 'vector'; icon: string; width?: number; height?: number; }; }</code> | buttonNearDone allows for a creation of a custom button near the done/close button. The button is only shown when toolbarType is not "activity", "navigation", or "blank". For Android: - iconType must be "asset" - icon path should be in the public folder (e.g. "monkey.svg") - width and height are optional, defaults to 48dp - button is positioned at the end of toolbar with 8dp margin For iOS: - iconType can be "sf-symbol" or "asset" - for sf-symbol, icon should be the symbol name - for asset, icon should be the asset name |                                                            | 6.7.0  |
| **`textZoom`**                         | <code>number</code>                                                                                                                                                    | textZoom: sets the text zoom of the page in percent. Allows users to increase or decrease the text size for better readability.                                                                                                                                                                                                                                                                                                                                                                                                               | <code>100</code>                                           | 7.6.0  |
| **`preventDeeplink`**                  | <code>boolean</code>                                                                                                                                                   | preventDeeplink: if true, the deeplink will not be opened, if false the deeplink will be opened when clicked on the link. on IOS each schema need to be added to info.plist file under LSApplicationQueriesSchemes when false to make it work.                                                                                                                                                                                                                                                                                                | <code>false</code>                                         | 0.1.0  |


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
| **`url`** | <code>string</code> | Emit when the url changes | 0.0.1 |


#### BtnEvent

| Prop      | Type                | Description                    | Since |
| --------- | ------------------- | ------------------------------ | ----- |
| **`url`** | <code>string</code> | Emit when a button is clicked. | 0.0.1 |


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
| **`ACTIVITY`**   | <code>"activity"</code>   | Shows a simple toolbar with just a close button and share button | 0.1.0 |
| **`NAVIGATION`** | <code>"navigation"</code> | Shows a full navigation toolbar with back/forward buttons        | 0.1.0 |
| **`BLANK`**      | <code>"blank"</code>      | Shows no toolbar                                                 | 0.1.0 |


#### BackgroundColor

| Members     | Value                |
| ----------- | -------------------- |
| **`WHITE`** | <code>"white"</code> |
| **`BLACK`** | <code>"black"</code> |

</docgen-api>

**Credits**
 - [WKWebViewController](https://github.com/Meniny/WKWebViewController) - for iOS
 - [CapBrowser](https://github.com/gadapa-rakesh/CapBrowser) - For the base in capacitor v2
