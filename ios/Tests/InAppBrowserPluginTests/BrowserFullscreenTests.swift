import Capacitor
import ObjectiveC
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
    func testInactiveFirstAppearanceKeepsStartupPending() throws {
        let controller = WKWebViewController(source: .remote(URL(string: "https://example.com")!))
        let navigation = BrowserNavigationController(rootViewController: controller)
        navigation.loadViewIfNeeded()
        controller.loadViewIfNeeded()
        controller.pendingStartupFullscreen = true
        let getter = try XCTUnwrap(class_getInstanceMethod(UIApplication.self, #selector(getter: UIApplication.applicationState)))
        let inactive: @convention(block) (UIApplication) -> Int = { _ in UIApplication.State.inactive.rawValue }
        let implementation = imp_implementationWithBlock(inactive)
        let original = method_setImplementation(getter, implementation)
        defer {
            method_setImplementation(getter, original)
            imp_removeBlock(implementation)
            controller.cleanupWebView()
        }

        controller.viewWillAppear(false)

        XCTAssertTrue(controller.pendingStartupFullscreen)
        XCTAssertFalse(controller.isBrowserFullscreen)
        XCTAssertFalse(navigation.isNavigationBarHidden)
    }

    @MainActor
    func testBecomingActiveAppliesVisibleStartupOnlyOnce() async {
        let controller = WKWebViewController(source: .remote(URL(string: "https://example.com")!))
        let navigation = BrowserNavigationController(rootViewController: controller)
        let presenter = UIViewController()
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = presenter
        window.makeKeyAndVisible()
        await withCheckedContinuation { continuation in
            presenter.present(navigation, animated: false) { continuation.resume() }
        }
        let plugin = CapgoInAppBrowserPlugin()
        plugin.webViewController = controller
        plugin.navigationWebViewController = navigation
        // Model a startup request that was still pending when presentation finished inactive.
        controller.pendingStartupFullscreen = true
        let notification = NSNotification(name: UIApplication.didBecomeActiveNotification, object: nil)
        plugin.appDidBecomeActive(notification)
        XCTAssertTrue(controller.isBrowserFullscreen)
        XCTAssertFalse(controller.pendingStartupFullscreen)
        controller.setBrowserFullscreen(false)
        plugin.appDidBecomeActive(notification)
        XCTAssertFalse(controller.isBrowserFullscreen)
        await withCheckedContinuation { continuation in
            presenter.dismiss(animated: false) { continuation.resume() }
        }
        window.isHidden = true
        controller.cleanupWebView()
    }

    @MainActor
    func testBecomingActiveLeavesHiddenStartupPendingUntilPresentation() async {
        let controller = WKWebViewController(source: .remote(URL(string: "https://example.com")!))
        let navigation = BrowserNavigationController(rootViewController: controller)
        let plugin = CapgoInAppBrowserPlugin()
        plugin.webViewController = controller
        plugin.navigationWebViewController = navigation
        controller.pendingStartupFullscreen = true
        plugin.appDidBecomeActive(NSNotification(name: UIApplication.didBecomeActiveNotification, object: nil))
        XCTAssertTrue(controller.pendingStartupFullscreen)
        XCTAssertFalse(controller.isBrowserFullscreen)
        XCTAssertNil(controller.viewIfLoaded?.window)

        let presenter = UIViewController()
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = presenter
        window.makeKeyAndVisible()
        await withCheckedContinuation { continuation in
            presenter.present(navigation, animated: false) { continuation.resume() }
        }
        XCTAssertTrue(controller.isBrowserFullscreen)
        XCTAssertFalse(controller.pendingStartupFullscreen)
        await withCheckedContinuation { continuation in
            presenter.dismiss(animated: false) { continuation.resume() }
        }
        window.isHidden = true
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
    func testExitButtonPositionAdaptsToOrientationAndKeepsTheTouchTarget() throws {
        let controller = WKWebViewController(source: nil)
        let navigation = BrowserNavigationController(rootViewController: controller)
        navigation.loadViewIfNeeded()
        controller.loadViewIfNeeded()
        controller.setBrowserFullscreen(true)
        let button = try XCTUnwrap(controller.browserFullscreen.exitButton)
        let top = try XCTUnwrap(controller.browserFullscreen.exitButtonTop)
        let trailing = try XCTUnwrap(controller.browserFullscreen.exitButtonTrailing)

        controller.view.bounds.size = CGSize(width: 393, height: 852)
        controller.viewDidLayoutSubviews()
        XCTAssertEqual(trailing.constant, 0)
        XCTAssertEqual(controller.view.safeAreaInsets.top + top.constant,
                       max(8, controller.view.safeAreaInsets.top - 8))

        controller.view.bounds.size = CGSize(width: 852, height: 393)
        controller.viewDidLayoutSubviews()
        XCTAssertEqual(top.constant, 8)
        XCTAssertEqual(trailing.constant, 8)
        XCTAssertTrue(controller.browserFullscreen.exitButton === button)
        XCTAssertEqual(button.constraints.filter { $0.constant == 44 }.count, 2)

        controller.view.bounds.size = CGSize(width: 393, height: 852)
        controller.viewDidLayoutSubviews()
        XCTAssertEqual(trailing.constant, 0)
        XCTAssertGreaterThanOrEqual(controller.view.safeAreaInsets.top + top.constant, 8)
        controller.setBrowserFullscreen(false)
        XCTAssertNil(controller.browserFullscreen.exitButtonTop)
        XCTAssertNil(controller.browserFullscreen.exitButtonTrailing)
        controller.cleanupWebView()
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
