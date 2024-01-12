# @capgo/inappbrowser
  <a href="https://capgo.app/"><img src='https://raw.githubusercontent.com/Cap-go/capgo/main/assets/capgo_banner.png' alt='Capgo - Instant updates for capacitor'/></a>
  <div align="center">
<h2><a href="https://capgo.app/">Check out: Capgo — Instant updates for capacitor</a></h2>
</div>
Capacitor plugin in app browser with urlChangeEvent

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

### Camera usage

if you need the Camera to work in Android, you need to add the following to your `AndroidManifest.xml` file:

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

Then the permission will be asked when the camera is used.

## API

<docgen-index>

* [`open(...)`](#open)
* [`clearCookies(...)`](#clearcookies)
* [`getCookies(...)`](#getcookies)
* [`close()`](#close)
* [`openWebView(...)`](#openwebview)
* [`executeScript(...)`](#executescript)
* [`setUrl(...)`](#seturl)
* [`addListener('urlChangeEvent', ...)`](#addlistenerurlchangeevent)
* [`addListener('closeEvent', ...)`](#addlistenercloseevent)
* [`addListener('confirmBtnClicked', ...)`](#addlistenerconfirmbtnclicked)
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

Open url in a new window fullscreen

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

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### openWebView(...)

```typescript
openWebView(options: OpenWebViewOptions) => Promise<any>
```

Open url in a new webview with toolbars

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


### setUrl(...)

```typescript
setUrl(options: { url: string; }) => Promise<any>
```

| Param         | Type                          |
| ------------- | ----------------------------- |
| **`options`** | <code>{ url: string; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### addListener('urlChangeEvent', ...)

```typescript
addListener(eventName: "urlChangeEvent", listenerFunc: UrlChangeListener) => Promise<PluginListenerHandle> & PluginListenerHandle
```

Listen for url change, only for openWebView

| Param              | Type                                                            |
| ------------------ | --------------------------------------------------------------- |
| **`eventName`**    | <code>'urlChangeEvent'</code>                                   |
| **`listenerFunc`** | <code><a href="#urlchangelistener">UrlChangeListener</a></code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt; & <a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

**Since:** 0.0.1

--------------------


### addListener('closeEvent', ...)

```typescript
addListener(eventName: "closeEvent", listenerFunc: UrlChangeListener) => Promise<PluginListenerHandle> & PluginListenerHandle
```

Listen for close click only for openWebView

| Param              | Type                                                            |
| ------------------ | --------------------------------------------------------------- |
| **`eventName`**    | <code>'closeEvent'</code>                                       |
| **`listenerFunc`** | <code><a href="#urlchangelistener">UrlChangeListener</a></code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt; & <a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

**Since:** 0.4.0

--------------------


### addListener('confirmBtnClicked', ...)

```typescript
addListener(eventName: "confirmBtnClicked", listenerFunc: ConfirmBtnListener) => Promise<PluginListenerHandle> & PluginListenerHandle
```

Will be triggered when user clicks on confirm button when disclaimer is required, works only on iOS

| Param              | Type                                                              |
| ------------------ | ----------------------------------------------------------------- |
| **`eventName`**    | <code>'confirmBtnClicked'</code>                                  |
| **`listenerFunc`** | <code><a href="#confirmbtnlistener">ConfirmBtnListener</a></code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt; & <a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

**Since:** 0.0.1

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

| Prop                         | Type                                        | Description                                                                                                           | Since |
| ---------------------------- | ------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- | ----- |
| **`url`**                    | <code>string</code>                         | Target URL to load.                                                                                                   | 0.1.0 |
| **`headers`**                | <code><a href="#headers">Headers</a></code> | <a href="#headers">Headers</a> to send with the request.                                                              | 0.1.0 |
| **`isPresentAfterPageLoad`** | <code>boolean</code>                        | if true, the browser will be presented after the page is loaded, if false, the browser will be presented immediately. | 0.1.0 |
| **`preventDeeplink`**        | <code>boolean</code>                        |                                                                                                                       |       |


#### Headers


#### ClearCookieOptions

| Prop        | Type                 |
| ----------- | -------------------- |
| **`url`**   | <code>string</code>  |
| **`cache`** | <code>boolean</code> |


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

| Prop                                   | Type                                                            | Description                                                                                                                                                                       | Default                                                    | Since  |
| -------------------------------------- | --------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- | ------ |
| **`url`**                              | <code>string</code>                                             | Target URL to load.                                                                                                                                                               |                                                            | 0.1.0  |
| **`headers`**                          | <code><a href="#headers">Headers</a></code>                     | <a href="#headers">Headers</a> to send with the request.                                                                                                                          |                                                            | 0.1.0  |
| **`shareDisclaimer`**                  | <code><a href="#disclaimeroptions">DisclaimerOptions</a></code> | share options                                                                                                                                                                     |                                                            | 0.1.0  |
| **`toolbarType`**                      | <code><a href="#toolbartype">ToolBarType</a></code>             | Toolbar type                                                                                                                                                                      | <code>ToolBarType.DEFAULT</code>                           | 0.1.0  |
| **`shareSubject`**                     | <code>string</code>                                             | Share subject                                                                                                                                                                     |                                                            | 0.1.0  |
| **`title`**                            | <code>string</code>                                             | Title of the browser                                                                                                                                                              | <code>'New Window'</code>                                  | 0.1.0  |
| **`backgroundColor`**                  | <code><a href="#backgroundcolor">BackgroundColor</a></code>     | Background color of the browser, only on IOS                                                                                                                                      | <code>BackgroundColor.BLACK</code>                         | 0.1.0  |
| **`activeNativeNavigationForWebview`** | <code>boolean</code>                                            | If true, active the native navigation within the webview, Android only                                                                                                            | <code>false</code>                                         |        |
| **`disableGoBackOnNativeApplication`** | <code>boolean</code>                                            | Disable the possibility to go back on native application, usefull to force user to stay on the webview, Android only                                                              | <code>false</code>                                         |        |
| **`isPresentAfterPageLoad`**           | <code>boolean</code>                                            | Open url in a new window fullscreen isPresentAfterPageLoad: if true, the browser will be presented after the page is loaded, if false, the browser will be presented immediately. | <code>false</code>                                         | 0.1.0  |
| **`isInspectable`**                    | <code>boolean</code>                                            | Whether the website in the webview is inspectable or not, ios only                                                                                                                | <code>false</code>                                         |        |
| **`isAnimated`**                       | <code>boolean</code>                                            | Whether the webview opening is animated or not, ios only                                                                                                                          | <code>true</code>                                          |        |
| **`showReloadButton`**                 | <code>boolean</code>                                            | Shows a reload button that reloads the web page                                                                                                                                   | <code>false</code>                                         | 1.0.15 |
| **`closeModal`**                       | <code>boolean</code>                                            | CloseModal: if true a confirm will be displayed when user clicks on close button, if false the browser will be closed immediately.                                                | <code>false</code>                                         | 1.1.0  |
| **`closeModalTitle`**                  | <code>string</code>                                             | CloseModalTitle: title of the confirm when user clicks on close button, only on IOS                                                                                               | <code>'Close'</code>                                       | 1.1.0  |
| **`closeModalDescription`**            | <code>string</code>                                             | CloseModalDescription: description of the confirm when user clicks on close button, only on IOS                                                                                   | <code>'Are you sure you want to close this window?'</code> | 1.1.0  |
| **`closeModalOk`**                     | <code>string</code>                                             | CloseModalOk: text of the confirm button when user clicks on close button, only on IOS                                                                                            | <code>'Close'</code>                                       | 1.1.0  |
| **`closeModalCancel`**                 | <code>string</code>                                             | CloseModalCancel: text of the cancel button when user clicks on close button, only on IOS                                                                                         | <code>'Cancel'</code>                                      | 1.1.0  |
| **`visibleTitle`**                     | <code>boolean</code>                                            | visibleTitle: if true the website title would be shown else shown empty                                                                                                           | <code>true</code>                                          | 1.2.5  |
| **`toolbarColor`**                     | <code>string</code>                                             | toolbarColor: color of the toolbar in hex format                                                                                                                                  | <code>'#ffffff''</code>                                    | 1.2.5  |
| **`showArrow`**                        | <code>boolean</code>                                            | showArrow: if true an arrow would be shown instead of cross for closing the window                                                                                                | <code>false</code>                                         | 1.2.5  |


#### DisclaimerOptions

| Prop             | Type                |
| ---------------- | ------------------- |
| **`title`**      | <code>string</code> |
| **`message`**    | <code>string</code> |
| **`confirmBtn`** | <code>string</code> |
| **`cancelBtn`**  | <code>string</code> |


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


#### ConfirmBtnListener

<code>(state: <a href="#btnevent">BtnEvent</a>): void</code>


### Enums


#### ToolBarType

| Members          | Value                     |
| ---------------- | ------------------------- |
| **`ACTIVITY`**   | <code>"activity"</code>   |
| **`NAVIGATION`** | <code>"navigation"</code> |
| **`BLANK`**      | <code>"blank"</code>      |
| **`DEFAULT`**    | <code>""</code>           |


#### BackgroundColor

| Members     | Value                |
| ----------- | -------------------- |
| **`WHITE`** | <code>"white"</code> |
| **`BLACK`** | <code>"black"</code> |

</docgen-api>

**Credits**
 - [WKWebViewController](https://github.com/Meniny/WKWebViewController) - for iOS
 - [CapBrowser](https://github.com/gadapa-rakesh/CapBrowser) - For the base in capacitor v2
