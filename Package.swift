// swift-tools-version: 5.9
import PackageDescription

// `cap sync` rewrites the http-proxy-swizzle block below.
// The published default excludes HttpProxySchemeSwizzle.swift.
// App Store guideline 2.5.2 can reject a binary that contains
// method_exchangeImplementations even when the app never enables proxy.
// plugins.CapgoInAppBrowser.httpProxySwizzle: true compiles the swizzle instead.
// WebViewNativeUIEngine 1.0.11 does not use method_exchangeImplementations.
// It hooks UIWindow.sendEvent only after enableNativeUIEngine registers a projection.
let package = Package(
    name: "CapgoCapacitorInappbrowser",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "CapgoCapacitorInappbrowser",
            targets: ["InappbrowserPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0"),
        .package(url: "https://github.com/webview-native-ui/engine.git", exact: "1.0.11")
    ],
    targets: [
        .target(
            name: "InappbrowserPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm"),
                .product(name: "WebViewNativeUIEngine", package: "engine")
            ],
            path: "ios/Sources/InAppBrowserPlugin",
            // http-proxy-swizzle:begin
            // http-proxy-swizzle:disabled
            exclude: ["HttpProxySchemeSwizzle.swift"],
            // http-proxy-swizzle:end
            resources: [.process("Resources")]),
        .testTarget(
            name: "InappbrowserPluginTests",
            dependencies: ["InappbrowserPlugin"],
            path: "ios/Tests/InAppBrowserPluginTests")
    ]
)
