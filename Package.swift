// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapgoCapacitorInappbrowser",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "CapgoCapacitorInappbrowser",
            targets: ["InappbrowserPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.2.0")
    ],
    targets: [
        .target(
            name: "InappbrowserPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/InappbrowserPlugin"),
        .testTarget(
            name: "InappbrowserPluginTests",
            dependencies: ["InappbrowserPlugin"],
            path: "ios/Tests/InappbrowserPluginTests")
    ]
)
