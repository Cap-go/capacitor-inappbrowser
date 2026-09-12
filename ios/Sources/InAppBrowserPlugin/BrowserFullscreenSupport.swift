import UIKit
import WebKit

struct BrowserFullscreenState {
    var enabled = false
    var pendingStartup = false
    var baseline: BrowserFullscreenBaseline?
    var origin: URL?
    var exitButton: UIButton?
    var applyingLayout = false
}

struct BrowserFullscreenBaseline {
    let navigationBarHidden: Bool
    let toolbarHidden: Bool
    let capturesStatusBarAppearance: Bool
    var safeTop: Bool
    var safeBottom: Bool
}

enum BrowserFullscreenOrigin {
    static func isSame(_ lhs: URL?, _ rhs: URL?) -> Bool {
        guard let lhs, let rhs,
              let scheme = lhs.scheme?.lowercased(),
              let host = lhs.host?.lowercased(),
              !host.isEmpty else {
            return false
        }
        let defaultPort: Int?
        switch scheme {
        case "https": defaultPort = 443
        case "http": defaultPort = 80
        default: defaultPort = nil
        }
        return scheme == rhs.scheme?.lowercased()
            && host == rhs.host?.lowercased()
            && (lhs.port ?? defaultPort) == (rhs.port ?? defaultPort)
    }
}

// UINavigationController otherwise owns these preferences instead of its browser child.
final class BrowserNavigationController: UINavigationController {
    override var childForStatusBarHidden: UIViewController? {
        (topViewController as? WKWebViewController)?.isBrowserFullscreen == true
            ? topViewController : super.childForStatusBarHidden
    }

    override var childForHomeIndicatorAutoHidden: UIViewController? {
        (topViewController as? WKWebViewController)?.isBrowserFullscreen == true
            ? topViewController : super.childForHomeIndicatorAutoHidden
    }
}

extension WKWebViewController {
    var isBrowserFullscreen: Bool { browserFullscreen.enabled }
    var pendingStartupFullscreen: Bool {
        get { browserFullscreen.pendingStartup }
        set { browserFullscreen.pendingStartup = newValue }
    }

    public func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        setBrowserFullscreen(false)
        stopReloadGesture()
    }

    var supportsBrowserFullscreen: Bool {
        !isLayeredBehind && customWidth == nil && customHeight == nil && customX == nil && customY == nil
    }

    func setBrowserFullscreen(_ enabled: Bool) {
        browserFullscreen.pendingStartup = false
        guard enabled != browserFullscreen.enabled else { return }
        browserFullscreen.applyingLayout = true
        defer { browserFullscreen.applyingLayout = false }
        if enabled {
            guard supportsBrowserFullscreen, let navigationController else { return }
            browserFullscreen.baseline = BrowserFullscreenBaseline(
                navigationBarHidden: navigationController.isNavigationBarHidden,
                toolbarHidden: navigationController.isToolbarHidden,
                capturesStatusBarAppearance: navigationController.modalPresentationCapturesStatusBarAppearance,
                safeTop: enabledSafeTopMargin,
                safeBottom: enabledSafeBottomMargin
            )
            browserFullscreen.origin = capableWebView?.url ?? source?.remoteURL
            browserFullscreen.enabled = true
            navigationController.modalPresentationCapturesStatusBarAppearance = true
            navigationController.setNavigationBarHidden(true, animated: false)
            navigationController.setToolbarHidden(true, animated: false)
            updateSafeTopMargin(false)
            updateSafeBottomMargin(false)
            let button = UIButton(type: .system)
            button.setImage(UIImage(systemName: "arrow.down.right.and.arrow.up.left"), for: .normal)
            button.accessibilityLabel = NSLocalizedString("Exit fullscreen", comment: "Browser fullscreen exit button")
            button.accessibilityIdentifier = "inappbrowser.exitFullscreen"
            button.tintColor = .white
            button.backgroundColor = UIColor.black.withAlphaComponent(0.7)
            button.layer.cornerRadius = 22
            button.translatesAutoresizingMaskIntoConstraints = false
            button.addTarget(self, action: #selector(exitBrowserFullscreen), for: .touchUpInside)
            view.addSubview(button)
            NSLayoutConstraint.activate([
                button.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 8),
                button.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -8),
                button.widthAnchor.constraint(equalToConstant: 44),
                button.heightAnchor.constraint(equalToConstant: 44)
            ])
            browserFullscreen.exitButton = button
        } else {
            browserFullscreen.enabled = false
            // Close nested video natively even when page JavaScript is stalled.
            capableWebView?.closeAllMediaPresentations(completionHandler: nil)
            browserFullscreen.exitButton?.removeFromSuperview()
            browserFullscreen.exitButton = nil
            if let baseline = browserFullscreen.baseline {
                navigationController?.modalPresentationCapturesStatusBarAppearance =
                    baseline.capturesStatusBarAppearance
                navigationController?.setNavigationBarHidden(baseline.navigationBarHidden, animated: false)
                navigationController?.setToolbarHidden(baseline.toolbarHidden, animated: false)
                updateSafeTopMargin(baseline.safeTop)
                updateSafeBottomMargin(baseline.safeBottom)
            }
            browserFullscreen.baseline = nil
            browserFullscreen.origin = nil
        }
        statusBarBackgroundView?.isHidden = enabled
        setNeedsStatusBarAppearanceUpdate()
        setNeedsUpdateOfHomeIndicatorAutoHidden()
        navigationController?.setNeedsStatusBarAppearanceUpdate()
        navigationController?.presentingViewController?.setNeedsStatusBarAppearanceUpdate()
        navigationController?.setNeedsUpdateOfHomeIndicatorAutoHidden()
        capBrowserPlugin?.notifyListeners("fullscreenChange", data: ["id": instanceId, "enabled": enabled])
    }

    @objc func exitBrowserFullscreen() {
        setBrowserFullscreen(false)
    }

    func exitFullscreenIfOriginChanges(to url: URL?) {
        if browserFullscreen.enabled || browserFullscreen.pendingStartup,
           !BrowserFullscreenOrigin.isSame(browserFullscreen.origin ?? source?.remoteURL, url) {
            setBrowserFullscreen(false)
        }
    }
}
