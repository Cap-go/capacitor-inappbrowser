// swift-tools-version: 5.9
import PackageDescription

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
            path: "ios/Sources/InAppBrowserPlugin"),
        .testTarget(
            name: "InappbrowserPluginTests",
            dependencies: ["InappbrowserPlugin"],
            path: "ios/Tests/InAppBrowserPluginTests")
    ]
)
