## Maestro

This folder contains Android regression coverage for the example app.

### Download handling smoke test

`download-handling-android.yaml` opens the example app's auto-download demo and verifies that the downloaded blob file is rendered back inside the in-app browser.

### Run locally

1. Start an Android emulator or connect a device with `adb`.
2. Install Maestro CLI.
3. From the repo root, run `bun run test:maestro:android`.
