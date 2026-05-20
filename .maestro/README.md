## Maestro

This folder contains Maestro coverage for the example app.

### Example app smoke test

`android/example-app-smoke.yaml` runs the broader example app smoke test. It uses a self-contained feature harness in the app to exercise managed WebView APIs, proxy handling, JavaScript messaging, screenshots, visibility controls, dimensions, cookies, navigation, reload, popup handling, close events, and then runs the native download handling smoke test.

### Download handling smoke test

`download-handling-android.yaml` opens the example app's auto-download demo and verifies that the downloaded blob file is rendered back inside the in-app browser.

### Run locally

1. Start an Android emulator or connect a device with `adb`.
2. Install Maestro CLI.
3. From the repo root, run `bun run test:maestro:android`.
