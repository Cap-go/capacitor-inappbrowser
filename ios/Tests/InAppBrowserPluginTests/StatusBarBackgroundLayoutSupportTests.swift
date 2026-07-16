import UIKit
import XCTest
@testable import InappbrowserPlugin

final class StatusBarBackgroundLayoutSupportTests: XCTestCase {
    private let exampleURL = URL(string: "https://example.com")!

    func testStatusBarBackgroundPlacementSkipsBlankPassThrough() {
        XCTAssertEqual(
            StatusBarBackgroundLayoutSupport.placement(
                isPassThroughOverlay: true,
                blankNavigationTab: true,
                navigationBarInHostHierarchy: false
            ),
            .skip
        )
    }

    func testStatusBarBackgroundPlacementSkipsBlankFramedOverlay() {
        XCTAssertEqual(
            StatusBarBackgroundLayoutSupport.placement(
                isPassThroughOverlay: false,
                blankNavigationTab: true,
                navigationBarInHostHierarchy: true,
                isFramedCustomOverlay: true
            ),
            .skip
        )
    }

    func testStatusBarBackgroundPlacementPinsCompactPassThroughToNavBar() {
        XCTAssertEqual(
            StatusBarBackgroundLayoutSupport.placement(
                isPassThroughOverlay: true,
                blankNavigationTab: false,
                navigationBarInHostHierarchy: true
            ),
            .host(pinToNavigationBar: true)
        )
    }

    func testStatusBarBackgroundPlacementUsesHeightWhenBlankFullscreen() {
        XCTAssertEqual(
            StatusBarBackgroundLayoutSupport.placement(
                isPassThroughOverlay: false,
                blankNavigationTab: true,
                navigationBarInHostHierarchy: false
            ),
            .host(pinToNavigationBar: false)
        )
    }

    func testStatusBarBackgroundHostPrefersFramedContentForPassThrough() {
        let passThrough = PassThroughView(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        let framed = UIView(frame: CGRect(x: 0, y: 70, width: 390, height: 600))
        passThrough.framedContentView = framed

        let host = StatusBarBackgroundLayoutSupport.hostView(
            navigationControllerView: passThrough,
            framedContentView: framed
        )

        XCTAssertTrue(host === framed)
    }

    func testSetupStatusBarBackgroundDoesNotCrashForBlankHiddenNavBar() {
        let controller = WKWebViewController(
            source: .remote(exampleURL)
        )
        controller.blankNavigationTab = true
        let navigationController = UINavigationController(rootViewController: controller)
        navigationController.setNavigationBarHidden(true, animated: false)
        navigationController.loadViewIfNeeded()
        controller.loadViewIfNeeded()

        controller.setupStatusBarBackground(color: .white)

        XCTAssertNotNil(controller.statusBarBackgroundView)
        XCTAssertEqual(controller.statusBarBackgroundView?.superview, navigationController.view)
        let pinnedToNavBar = controller.statusBarBackgroundView?.constraints.contains {
            ($0.secondItem as? UINavigationBar) === navigationController.navigationBar
                || ($0.firstItem as? UINavigationBar) === navigationController.navigationBar
        } ?? false
        XCTAssertFalse(pinnedToNavBar)
    }

    func testSetupStatusBarBackgroundKeepsPassThroughClearWithCustomOffset() {
        let controller = WKWebViewController(
            source: .remote(exampleURL)
        )
        let navigationController = UINavigationController(rootViewController: controller)
        navigationController.loadViewIfNeeded()
        controller.loadViewIfNeeded()

        let passThrough = PassThroughView(frame: UIScreen.main.bounds)
        passThrough.backgroundColor = .clear
        let originalView = navigationController.view!
        navigationController.view = passThrough
        passThrough.addSubview(originalView)
        passThrough.framedContentView = originalView
        passThrough.targetFrame = CGRect(x: 0, y: 200, width: 390, height: 600)
        originalView.frame = passThrough.targetFrame!

        controller.setupStatusBarBackground(color: .white)

        XCTAssertEqual(passThrough.backgroundColor, UIColor.clear)
        XCTAssertEqual(controller.statusBarBackgroundView?.superview, originalView)
        XCTAssertFalse(passThrough.subviews.contains { $0 === controller.statusBarBackgroundView })
    }

    func testSetupStatusBarBackgroundSkipsBlankPassThroughOverlay() {
        let controller = WKWebViewController(
            source: .remote(exampleURL)
        )
        controller.blankNavigationTab = true
        let navigationController = UINavigationController(rootViewController: controller)
        navigationController.loadViewIfNeeded()
        controller.loadViewIfNeeded()

        let passThrough = PassThroughView(frame: UIScreen.main.bounds)
        let originalView = navigationController.view!
        navigationController.view = passThrough
        passThrough.addSubview(originalView)
        passThrough.framedContentView = originalView
        navigationController.setNavigationBarHidden(true, animated: false)

        controller.setupStatusBarBackground(color: .white)

        XCTAssertNil(controller.statusBarBackgroundView)
        XCTAssertEqual(passThrough.backgroundColor, UIColor.clear)
    }

    func testSetupStatusBarBackgroundSkipsBlankFramedCustomOverlay() {
        let controller = WKWebViewController(
            source: .remote(exampleURL)
        )
        controller.blankNavigationTab = true
        controller.customHeight = 600
        controller.customY = 100
        XCTAssertTrue(controller.shouldPresentAsFramedOverlay)

        let navigationController = UINavigationController(rootViewController: controller)
        navigationController.setNavigationBarHidden(true, animated: false)
        navigationController.loadViewIfNeeded()
        controller.loadViewIfNeeded()

        controller.setupStatusBarBackground(color: .white)

        XCTAssertNil(controller.statusBarBackgroundView)
    }
}
