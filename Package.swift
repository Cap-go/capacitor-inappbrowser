// swift-tools-version: 5.9
import PackageDescription

// `cap sync` rewrites the http-proxy-swizzle block below.
// Default keeps the swizzle so existing proxy apps stay unchanged.
// plugins.CapgoInAppBrowser.httpProxySwizzle: false compiles the stub instead.
let package = Package(
    name: "CapgoCapacitorInappbrowser",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "CapgoCapacitorInappbrowser",
            targets: ["InappbrowserPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0")
    ],
    targets: [
        .target(
            name: "InappbrowserPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/InAppBrowserPlugin",
            // http-proxy-swizzle:begin
            // http-proxy-swizzle:enabled
            exclude: ["HttpProxySchemeSwizzleStub.swift"],
            // http-proxy-swizzle:end
            resources: [.process("Resources")]),
        .testTarget(
            name: "InappbrowserPluginTests",
            dependencies: ["InappbrowserPlugin"],
            path: "ios/Tests/InAppBrowserPluginTests")
    ]
)
