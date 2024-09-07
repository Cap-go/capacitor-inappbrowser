## Button near done


### Introduction

This plugin allows for a creation of a custom button near the "done" button. In order for this button to work the following conditions must be met:

 - The button image **MUST** be an SVG
 - The `ToolBarType` must be set to `default`

### Showcase

This button will look in the following way:

#### Android

![Alt text](/assets/android-button-near-done.png "Android button near done")

#### IOS

![Alt text](/assets/ios-button-near-done.png "Ios button near done")

###

Config options:

<table>
  <tr>
    <td>buttonNearDone</td>
  </tr>
  <tr>
    <td>
      <table>
        <tr>
          <td>ios</td>
          <td>android</td>
        </tr>
        <tr>
        <td>
          <table>
            <tr>
              <td>name</td>
              <td>type</td>
              <td>decryption</td>
            </tr>
            <tr>
              <td>iconType</td>
              <td>'sf-symbol' | 'asset'</td>
              <td>
                If set to <code>sf-symbol</code>, it's going to use <a href="https:/developer.apple.com/sf-symbols/">the sf-symbol library</a>
                <br/>
                Otherwise, it's going to use a file from <code>Assets.xcassets</code>
              </td>
            </tr>
            <tr>
              <td>icon</td>
              <td>String</td>
              <td>The path/name of the icon to use</td>
            </tr>
          </table>
        </td>
        <td>
          <table>
            <tr>
              <td>name</td>
              <td>type</td>
              <td>description</td>
            </tr>
            <tr>
              <td>iconType</td>
              <td>'asset'</td>
              <td>
                It <b>MUST</b> always be equal to <code>asset</code>!
                <br/>
                It's going to read the main <code>assets</code> of your app
              </td>
            </tr>
            <tr>
              <td>icon</td>
              <td>String</td>
              <td>The path of the icon to use</td>
            </tr>
            <tr>
              <td>width</td>
              <td>number?</td>
              <td>
                An optional number describing the width of the button.
                </br>
                Defaults to <code>48</code>
              </td>
            </tr>
            <tr>
              <td>height</td>
              <td>number?</td>
              <td>
                An optional number describing the height of the button.
                </br>
                Defaults to <code>48</code>
              </td>
            </tr>
          </table>
        </td>
      </table>
    </td>
  </tr>
</table>


### `asset` `iconType` on IOS

First, please open your app in xcode.
Then, please add an svg file into `Assets.xcassets` like so:

![Alt text](/assets/xcode-assets.png "xcode assets")

Legend:

 1. The file where you need to add your icon to
 2. Your icon
 3. The `1x` size. The plugin will always use the `1x` size

Later, you would use it as:
```ts
InAppBrowser.openWebView({ 
  url: WEB_URL, 
  buttonNearDone: { 
    ios: { icon: 'monkey', iconType: 'asset' },
  } 
})
```

### `asset` `iconType` on Android

First, please open your app in vscode.
Then, please add an svg file into `public/your_file.svg` like so:

![Alt text](/assets/android-asset-vscode.png "xcode assets")

Legend:

 1. The folder where you need the file into
 2. Your icon

Later, you would use it as:
```ts
InAppBrowser.openWebView({ 
  url: WEB_URL,
  buttonNearDone: { 
    android: { icon: 'public/monkey.svg', iconType: 'asset' }
  } 
})
```

### Configuration for both platforms

If you use both platforms, you would use the following config:

```ts
InAppBrowser.openWebView({ 
  url: WEB_URL,
  buttonNearDone: { 
    ios: { icon: 'monkey', iconType: 'resource' },
    android: { icon: 'public/monkey.svg', iconType: 'asset' }
  } 
})
```

### Listening for `buttonNearDoneClick` events

```ts
InAppBrowser.addListener('buttonNearDoneClick', async (msg) => {
  // Write your code here
  await InAppBrowser.setUrl({ url: 'https://web.capgo.app/login' })
})
```