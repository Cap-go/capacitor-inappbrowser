// swift-tools-version: 5.9
import Foundation
import PackageDescription

// Off unless the app opts in. Leaving this unset keeps method_exchangeImplementations
// out of the compiled plugin, which is what App Store review scans.
let enableHttpProxySwizzle = ProcessInfo.processInfo.environment["CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE"] == "1"
let httpProxySwizzleSettings: [SwiftSetting] = enableHttpProxySwizzle ? [.define("CAPGO_INAPPBROWSER_HTTP_PROXY_SWIZZLE")] : []

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
            resources: [.process("Resources")],
            swiftSettings: httpProxySwizzleSettings),
        .testTarget(
            name: "InappbrowserPluginTests",
            dependencies: ["InappbrowserPlugin"],
            path: "ios/Tests/InAppBrowserPluginTests",
            swiftSettings: httpProxySwizzleSettings)
    ]
)
