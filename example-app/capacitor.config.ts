import type { CapacitorConfig } from '@capacitor/cli';

import pkg from './package.json';

const config: CapacitorConfig = {
  "appId": "app.capgo.inappbrowser",
  "appName": "Inappbrowser Example",
  "webDir": "dist",
  "plugins": {
    "SplashScreen": {
      "launchAutoHide": true
    },
    "CapacitorUpdater": {
      "appId": "app.capgo.inappbrowser",
      "autoUpdate": true,
      "autoSplashscreen": true,
      "directUpdate": "always",
      "defaultChannel": "production",
      "version": pkg.version
    }
  }
};

export default config;
