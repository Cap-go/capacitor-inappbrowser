import XCTest
import WebKit
@testable import InappbrowserPlugin

final class PreferredContentModeSupportTests: XCTestCase {
    func testNormalizesSupportedModes() {
        XCTAssertEqual(PreferredContentModeSupport.normalizedMode("mobile"), "mobile")
        XCTAssertEqual(PreferredContentModeSupport.normalizedMode("DESKTOP"), "desktop")
        XCTAssertEqual(PreferredContentModeSupport.normalizedMode("Recommended"), "recommended")
    }

    func testRejectsUnknownModes() {
        XCTAssertNil(PreferredContentModeSupport.normalizedMode("tablet"))
    }

    func testResolvePrefersPerOpenValue() {
        let resolved = PreferredContentModeSupport.resolve(
            perOpenValue: "desktop",
            pluginConfigValue: "mobile",
            legacyPluginConfigValue: "recommended",
            capacitorConfigValue: "recommended"
        )
        XCTAssertEqual(resolved, "desktop")
    }

    func testResolveFallsBackToPluginConfig() {
        let resolved = PreferredContentModeSupport.resolve(
            perOpenValue: nil,
            pluginConfigValue: "mobile",
            legacyPluginConfigValue: "desktop",
            capacitorConfigValue: "recommended"
        )
        XCTAssertEqual(resolved, "mobile")
    }

    func testResolveFallsBackToLegacyPluginConfig() {
        let resolved = PreferredContentModeSupport.resolve(
            perOpenValue: nil,
            pluginConfigValue: nil,
            legacyPluginConfigValue: "desktop",
            capacitorConfigValue: "recommended"
        )
        XCTAssertEqual(resolved, "desktop")
    }

    func testResolveFallsBackToCapacitorConfig() {
        let resolved = PreferredContentModeSupport.resolve(
            perOpenValue: nil,
            pluginConfigValue: nil,
            legacyPluginConfigValue: nil,
            capacitorConfigValue: "desktop"
        )
        XCTAssertEqual(resolved, "desktop")
    }

    func testResolveReturnsNilWhenUnset() {
        XCTAssertNil(
            PreferredContentModeSupport.resolve(
                perOpenValue: nil,
                pluginConfigValue: nil,
                legacyPluginConfigValue: nil,
                capacitorConfigValue: nil
            )
        )
    }

    func testApplySetsWebViewConfigurationMode() {
        let configuration = WKWebViewConfiguration()
        PreferredContentModeSupport.apply(to: configuration, mode: "mobile")
        XCTAssertEqual(configuration.defaultWebpagePreferences.preferredContentMode, .mobile)
    }
}
