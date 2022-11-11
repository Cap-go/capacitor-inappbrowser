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

## API

<docgen-index>

* [`open(...)`](#open)
* [`close()`](#close)
* [`openWebView(...)`](#openwebview)
* [`setUrl(...)`](#seturl)
* [`addListener('urlChangeEvent', ...)`](#addlistenerurlchangeevent)
* [`addListener('confirmBtnClicked', ...)`](#addlistenerconfirmbtnclicked)
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

| Param         | Type                                                |
| ------------- | --------------------------------------------------- |
| **`options`** | <code><a href="#openoptions">OpenOptions</a></code> |

**Returns:** <code>Promise&lt;any&gt;</code>

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

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code><a href="#openwebviewoptions">OpenWebViewOptions</a></code> |

**Returns:** <code>Promise&lt;any&gt;</code>

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
addListener(eventName: 'urlChangeEvent', listenerFunc: UrlChangeListener) => Promise<PluginListenerHandle> & PluginListenerHandle
```

Listen for url change

| Param              | Type                                                            |
| ------------------ | --------------------------------------------------------------- |
| **`eventName`**    | <code>'urlChangeEvent'</code>                                   |
| **`listenerFunc`** | <code><a href="#urlchangelistener">UrlChangeListener</a></code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt; & <a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

**Since:** 0.0.1

--------------------


### addListener('confirmBtnClicked', ...)

```typescript
addListener(eventName: 'confirmBtnClicked', listenerFunc: ConfirmBtnListener) => Promise<PluginListenerHandle> & PluginListenerHandle
```

Will be triggered when user clicks on confirm button when disclaimer is required, works only on iOS

| Param              | Type                                                              |
| ------------------ | ----------------------------------------------------------------- |
| **`eventName`**    | <code>'confirmBtnClicked'</code>                                  |
| **`listenerFunc`** | <code><a href="#confirmbtnlistener">ConfirmBtnListener</a></code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt; & <a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

**Since:** 0.0.1

--------------------


### Interfaces


#### OpenOptions

| Prop                         | Type                                        |
| ---------------------------- | ------------------------------------------- |
| **`url`**                    | <code>string</code>                         |
| **`headers`**                | <code><a href="#headers">Headers</a></code> |
| **`isPresentAfterPageLoad`** | <code>boolean</code>                        |


#### Headers


#### OpenWebViewOptions

| Prop                         | Type                                                            |
| ---------------------------- | --------------------------------------------------------------- |
| **`url`**                    | <code>string</code>                                             |
| **`headers`**                | <code><a href="#headers">Headers</a></code>                     |
| **`shareDisclaimer`**        | <code><a href="#disclaimeroptions">DisclaimerOptions</a></code> |
| **`toolbarType`**            | <code><a href="#toolbartype">ToolBarType</a></code>             |
| **`shareSubject`**           | <code>string</code>                                             |
| **`title`**                  | <code>string</code>                                             |
| **`backgroundColor`**        | <code><a href="#backgroundcolor">BackgroundColor</a></code>     |
| **`isPresentAfterPageLoad`** | <code>boolean</code>                                            |


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


#### UrlChangeListener

<code>(state: <a href="#urlevent">UrlEvent</a>): void</code>


#### ConfirmBtnListener

<code>(state: <a href="#btnevent">BtnEvent</a>): void</code>


### Enums


#### ToolBarType

| Members          | Value                     |
| ---------------- | ------------------------- |
| **`ACTIVITY`**   | <code>'activity'</code>   |
| **`NAVIGATION`** | <code>'navigation'</code> |
| **`BLANK`**      | <code>'blank'</code>      |
| **`DEFAULT`**    | <code>''</code>           |


#### BackgroundColor

| Members     | Value                |
| ----------- | -------------------- |
| **`WHITE`** | <code>'white'</code> |
| **`BLACK`** | <code>'black'</code> |

</docgen-api>

**Credits**
 - [WKWebViewController](https://github.com/Meniny/WKWebViewController) - for iOS
 - [CapBrowser](https://github.com/gadapa-rakesh/CapBrowser) - For the base in capacitor v2
