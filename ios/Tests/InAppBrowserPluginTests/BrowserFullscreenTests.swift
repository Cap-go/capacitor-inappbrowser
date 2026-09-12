import Capacitor
import UIKit
import WebKit
import XCTest
@testable import InappbrowserPlugin

final class BrowserFullscreenTests: XCTestCase {
    func testOriginsNormalizeDefaultPortsButRejectSchemeHostAndPortChanges() {
        let origin = URL(string: "https://example.com/start")!
        XCTAssertTrue(BrowserFullscreenOrigin.isSame(origin, URL(string: "https://example.com:443/next#section")))
        XCTAssertFalse(BrowserFullscreenOrigin.isSame(origin, URL(string: "http://example.com/")))
        XCTAssertFalse(BrowserFullscreenOrigin.isSame(origin, URL(string: "https://other.example.com/")))
        XCTAssertFalse(BrowserFullscreenOrigin.isSame(origin, URL(string: "https://example.com:8443/")))
        XCTAssertFalse(BrowserFullscreenOrigin.isSame(origin, URL(string: "data:text/html,test")))
        XCTAssertFalse(BrowserFullscreenOrigin.isSame(nil, origin))
    }

    func testSetterRejectsMissingAndNonBooleanEnabledValues() throws {
        let plugin = CapgoInAppBrowserPlugin()
        for options: [String: Any] in [[:], ["enabled": 1], ["enabled": "true"], ["enabled": NSNull()]] {
            var rejected = false
            let call = try XCTUnwrap(CAPPluginCall(callbackId: "test", methodName: "setFullscreen", options: options, success: { _, _ in
                XCTFail("Invalid enabled must not resolve")
            }, error: { _ in
                rejected = true
            }))
            plugin.setFullscreen(call)
            XCTAssertTrue(rejected)
        }
    }

    @MainActor
    func testFullscreenPreservesWebViewAndRestoresNavigationAndSafeMargins() {
        let controller = WKWebViewController(source: .remote(URL(string: "https://example.com")!))
        controller.enabledSafeTopMargin = true
        controller.enabledSafeBottomMargin = true
        let navigation = BrowserNavigationController(rootViewController: controller)
        navigation.loadViewIfNeeded()
        controller.loadViewIfNeeded()
        navigation.setNavigationBarHidden(false, animated: false)
        let webView = controller.capableWebView
        let capturesStatusBarAppearance = navigation.modalPresentationCapturesStatusBarAppearance

        controller.setBrowserFullscreen(true)
        controller.setBrowserFullscreen(true)
        XCTAssertTrue(controller.isBrowserFullscreen)
        XCTAssertTrue(controller.prefersStatusBarHidden)
        XCTAssertTrue(controller.prefersHomeIndicatorAutoHidden)
        XCTAssertTrue(navigation.isNavigationBarHidden)
        XCTAssertFalse(controller.enabledSafeTopMargin)
        XCTAssertFalse(controller.enabledSafeBottomMargin)
        XCTAssertTrue(controller.capableWebView === webView)
        XCTAssertTrue(navigation.childForStatusBarHidden === controller)
        let exit = controller.view.subviews.compactMap { $0 as? UIButton }.first {
            $0.accessibilityIdentifier == "inappbrowser.exitFullscreen"
        }
        XCTAssertEqual(exit?.accessibilityLabel, "Exit fullscreen")
        XCTAssertEqual(exit?.constraints.filter { $0.constant == 44 }.count, 2)
        exit?.sendActions(for: .touchUpInside)

        XCTAssertFalse(controller.isBrowserFullscreen)
        XCTAssertFalse(controller.prefersStatusBarHidden)
        XCTAssertEqual(navigation.modalPresentationCapturesStatusBarAppearance, capturesStatusBarAppearance)
        XCTAssertFalse(navigation.isNavigationBarHidden)
        XCTAssertTrue(controller.enabledSafeTopMargin)
        XCTAssertTrue(controller.enabledSafeBottomMargin)
        XCTAssertTrue(controller.capableWebView === webView)
        XCTAssertNil(exit?.superview)
        controller.cleanupWebView()
    }

    @MainActor
    func testBlankToolbarAndDisabledMarginsRemainUnchangedAfterExit() {
        let controller = WKWebViewController(source: .remote(URL(string: "https://example.com")!))
        controller.blankNavigationTab = true
        controller.enabledSafeTopMargin = false
        controller.enabledSafeBottomMargin = false
        let navigation = BrowserNavigationController(rootViewController: controller)
        navigation.loadViewIfNeeded()
        controller.loadViewIfNeeded()
        navigation.setNavigationBarHidden(true, animated: false)
        controller.setBrowserFullscreen(true)
        controller.setBrowserFullscreen(false)
        controller.setBrowserFullscreen(false)
        XCTAssertTrue(navigation.isNavigationBarHidden)
        XCTAssertFalse(controller.enabledSafeTopMargin)
        XCTAssertFalse(controller.enabledSafeBottomMargin)
        controller.cleanupWebView()
    }

    @MainActor
    func testHiddenStartupCanBeCancelledWithoutApplyingFullscreen() {
        let controller = WKWebViewController()
        controller.pendingStartupFullscreen = true
        XCTAssertFalse(controller.isBrowserFullscreen)
        controller.setBrowserFullscreen(false)
        XCTAssertFalse(controller.pendingStartupFullscreen)
        XCTAssertFalse(controller.isViewLoaded)
    }

    @MainActor
    func testCrossOriginAndRendererTerminationExitWithoutReplacingWebView() throws {
        let controller = WKWebViewController(source: .remote(URL(string: "https://example.com")!))
        let navigation = BrowserNavigationController(rootViewController: controller)
        navigation.loadViewIfNeeded()
        controller.loadViewIfNeeded()
        let webView = try XCTUnwrap(controller.capableWebView)
        controller.setBrowserFullscreen(true)
        controller.exitFullscreenIfOriginChanges(to: URL(string: "https://example.com/next"))
        XCTAssertTrue(controller.isBrowserFullscreen)
        controller.exitFullscreenIfOriginChanges(to: URL(string: "https://other.example.com"))
        XCTAssertFalse(controller.isBrowserFullscreen)
        controller.setBrowserFullscreen(true)
        controller.webViewWebContentProcessDidTerminate(webView)
        XCTAssertFalse(controller.isBrowserFullscreen)
        XCTAssertTrue(controller.capableWebView === webView)
        controller.cleanupWebView()
    }

    @MainActor
    func testRuntimeMarginSettingsApplyOnExitAndOtherWebViewsStayUnchanged() {
        let first = WKWebViewController(source: .remote(URL(string: "https://example.com")!))
        let second = WKWebViewController(source: .remote(URL(string: "https://other.example.com")!))
        let navigation = BrowserNavigationController(rootViewController: first)
        navigation.loadViewIfNeeded()
        first.setBrowserFullscreen(true)
        first.updateSafeTopMargin(false)
        first.updateSafeBottomMargin(true)
        XCTAssertFalse(first.enabledSafeBottomMargin)
        XCTAssertFalse(second.isBrowserFullscreen)
        first.setBrowserFullscreen(false)
        XCTAssertFalse(first.enabledSafeTopMargin)
        XCTAssertTrue(first.enabledSafeBottomMargin)
        XCTAssertFalse(second.isBrowserFullscreen)
        first.cleanupWebView()
        second.cleanupWebView()
    }

    @MainActor
    func testFramedAndBehindHostViewsCannotEnterFullscreen() {
        let controller = WKWebViewController(source: .remote(URL(string: "https://example.com")!))
        controller.customHeight = 300
        XCTAssertFalse(controller.supportsBrowserFullscreen)
        controller.setBrowserFullscreen(true)
        XCTAssertFalse(controller.isBrowserFullscreen)
        controller.customHeight = nil
        controller.isLayeredBehind = true
        XCTAssertFalse(controller.supportsBrowserFullscreen)
    }
}
