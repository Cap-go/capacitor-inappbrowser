import Capacitor
import ObjectiveC
import UIKit
import WebKit
import XCTest
@testable import InappbrowserPlugin

final class BrowserFullscreenTests: XCTestCase {
    private final class BridgeHost: CAPBridgeViewController {
        var contentDirectory = FileManager.default.temporaryDirectory

        override func instanceDescriptor() -> InstanceDescriptor {
            let descriptor = InstanceDescriptor()
            descriptor.appLocation = contentDirectory
            return descriptor
        }
    }

    @MainActor
    private func call(_ method: (CAPPluginCall) -> Void, options: [String: Any] = [:]) async throws -> [String: Any] {
        try await withCheckedThrowingContinuation { continuation in
            let call = CAPPluginCall(callbackId: UUID().uuidString, methodName: "test", options: options, success: { result, _ in
                continuation.resume(returning: result?.data ?? [:])
            }, error: { error in
                continuation.resume(throwing: NSError(domain: "FullscreenTests", code: 1,
                                                      userInfo: [NSLocalizedDescriptionKey: error?.message ?? "Plugin call failed"]))
            })
            if let call {
                method(call)
            } else {
                continuation.resume(throwing: NSError(domain: "FullscreenTests", code: 2))
            }
        }
    }

    @MainActor
    func testDeferredPresentationBecomesActiveForFullscreenCommandsAndResume() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try "<html></html>".write(to: directory.appendingPathComponent("index.html"), atomically: true, encoding: .utf8)
        defer { try? FileManager.default.removeItem(at: directory) }
        let host = BridgeHost()
        host.contentDirectory = directory
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = host
        window.makeKeyAndVisible()
        let plugin = CapgoInAppBrowserPlugin()
        try XCTUnwrap(host.bridge as? CapacitorBridge).registerPluginInstance(plugin)

        let firstResult = try await call(plugin.openWebView, options: [
            "url": "https://first.example.test", "fullscreen": true, "isPresentAfterPageLoad": true
        ])
        let first = try XCTUnwrap(plugin.webViewController)
        first.capableWebView?.navigationDelegate = nil
        let firstNavigation = try XCTUnwrap(plugin.navigationWebViewController)
        let firstId = try XCTUnwrap(firstResult["id"] as? String)
        let secondResult = try await call(plugin.openWebView, options: [
            "url": "https://second.example.test", "isPresentAfterPageLoad": true
        ])
        let second = try XCTUnwrap(plugin.webViewController)
        let secondId = try XCTUnwrap(secondResult["id"] as? String)
        second.capableWebView?.navigationDelegate = nil
        let secondNavigation = try XCTUnwrap(plugin.navigationWebViewController)
        XCTAssertTrue(plugin.presentView(webViewId: secondId, isAnimated: false))
        await fulfillment(of: [XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            secondNavigation.presentingViewController != nil && !secondNavigation.isBeingPresented
        }, object: nil)], timeout: 5)

        XCTAssertTrue(plugin.presentView(webViewId: firstId, isAnimated: false))
        await fulfillment(of: [XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            firstNavigation.presentingViewController != nil && !firstNavigation.isBeingPresented
        }, object: nil)], timeout: 5)
        XCTAssertTrue(plugin.webViewController === first)
        let state = try await call(plugin.getFullscreen)
        XCTAssertEqual(state["enabled"] as? Bool, true)
        _ = try await call(plugin.setFullscreen, options: ["enabled": false])
        XCTAssertFalse(first.isBrowserFullscreen)
        XCTAssertFalse(second.isBrowserFullscreen)
        first.pendingStartupFullscreen = true
        plugin.appDidBecomeActive(NSNotification(name: UIApplication.didBecomeActiveNotification, object: nil))
        XCTAssertTrue(first.isBrowserFullscreen)

        _ = try await call(plugin.close, options: ["id": firstId])
        _ = try await call(plugin.close, options: ["id": secondId])
        window.isHidden = true
    }

    @MainActor
    func testPendingStartupKeepsItsOriginWhenSetUrlReplacesTheSource() throws {
        for (url, pending) in [("https://example.com/next", true), ("https://other.example.com", false)] {
            let controller = WKWebViewController(source: .remote(URL(string: "https://example.com/start")!))
            controller.pendingStartupFullscreen = true
            // setUrl replaces source before WebKit delivers its navigation decision.
            let destination = try XCTUnwrap(URL(string: url))
            controller.source = .remote(destination)
            controller.exitFullscreenIfOriginChanges(to: destination)
            XCTAssertEqual(controller.pendingStartupFullscreen, pending)
            XCTAssertFalse(controller.isBrowserFullscreen)
            controller.cleanupWebView()
        }
    }

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
