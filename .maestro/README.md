## Maestro

This folder contains Maestro coverage for the example app.

### Example app smoke test

`android/example-app-smoke.yaml` runs the broader example app smoke test. It uses a self-contained feature harness in the app to exercise managed WebView APIs, proxy handling, JavaScript messaging, screenshots, visibility controls, dimensions, cookies, navigation, reload, popup handling, close events, and then runs the native download handling smoke test.

### Download handling smoke test

`download-handling-android.yaml` opens the example app's auto-download demo and verifies that the downloaded blob file is rendered back inside the in-app browser.

### Visual regression (CI)

`visual-regression-assert.yaml` opens a blank-toolbar layout probe in the example app and compares emulator or simulator screenshots against baselines in `visual-regression/baselines/` (set `BASELINE_PATH` per platform). CI runs Android and iOS jobs in `.github/workflows/test.yml` with animations disabled and a fixed status bar.

Record new baselines locally with `visual-regression-record.yaml` after intentional UI changes.

### Deep link prevented smoke test

`deep-link-prevented-android.yaml` and `deep-link-prevented-ios.yaml` run the example app regression harness: a custom scheme must emit `deepLinkPrevented`, and a normal `https://` navigation must not.

### Download preview none (`downloadPreview: 'none'`)

`download-preview-none-android.yaml` and `download-preview-none-ios.yaml` run the example app harness in `example-app/src/js/download-preview-regression.js`. It opens a webview with `handleDownloads: true` and `downloadPreview: 'none'`, downloads a PDF and a text file, and checks the plugin's `downloadCompleted` events (`handledBy: 'none'`) plus that the same page instance keeps running. The flow then asserts the page is still on screen with no Open with sheet or Quick Look, and runs a failure case where an invalid `downloadPreview` rejects with and without `handleDownloads`. Run on Android with `bun run maestro:test:android:download-preview-none` and on iOS with `sh ./scripts/run-ios-maestro-download-preview-none.sh`.

### Run locally

1. Start an Android emulator or connect a device with `adb`.
2. Install Maestro CLI.
3. From the repo root, run `bun run test:maestro:android`.
