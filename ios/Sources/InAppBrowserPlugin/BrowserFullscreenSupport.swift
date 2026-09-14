import UIKit
import WebKit

struct BrowserFullscreenState {
    var enabled = false
    var pendingStartup = false
    var baseline: BrowserFullscreenBaseline?
    var origin: URL?
    var exitButton: UIButton?
    var exitButtonTop: NSLayoutConstraint?
    var exitButtonTrailing: NSLayoutConstraint?
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
            var configuration: UIButton.Configuration
            if #available(iOS 26.0, *) {
                configuration = .glass()
            } else {
                configuration = .gray()
                configuration.background.visualEffect = UIBlurEffect(style: .systemChromeMaterial)
                configuration.background.backgroundColor = .clear
            }
            configuration.cornerStyle = .capsule
            configuration.contentInsets = .zero
            configuration.image = UIImage(systemName: "arrow.down.right.and.arrow.up.left")
            configuration.preferredSymbolConfigurationForImage = UIImage.SymbolConfiguration(pointSize: 16, weight: .semibold)
            configuration.baseForegroundColor = .label
            let button = UIButton(configuration: configuration)
            button.accessibilityLabel = NSLocalizedString("Exit fullscreen", comment: "Browser fullscreen exit button")
            button.accessibilityHint = NSLocalizedString("Restores the browser controls", comment: "Browser fullscreen exit hint")
            button.accessibilityIdentifier = "inappbrowser.exitFullscreen"
            button.isPointerInteractionEnabled = true
            button.translatesAutoresizingMaskIntoConstraints = false
            button.addTarget(self, action: #selector(exitBrowserFullscreen), for: .touchUpInside)
            view.addSubview(button)
            let top = button.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor)
            let trailing = button.trailingAnchor.constraint(equalTo: view.layoutMarginsGuide.trailingAnchor)
            NSLayoutConstraint.activate([
                top, trailing,
                button.widthAnchor.constraint(equalToConstant: 44),
                button.heightAnchor.constraint(equalToConstant: 44)
            ])
            browserFullscreen.exitButton = button
            browserFullscreen.exitButtonTop = top
            browserFullscreen.exitButtonTrailing = trailing
            updateFullscreenExitButtonPosition()
        } else {
            browserFullscreen.enabled = false
            // Close nested video natively even when page JavaScript is stalled.
            capableWebView?.closeAllMediaPresentations(completionHandler: nil)
            browserFullscreen.exitButton?.removeFromSuperview()
            browserFullscreen.exitButton = nil
            browserFullscreen.exitButtonTop = nil
            browserFullscreen.exitButtonTrailing = nil
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

    func updateFullscreenExitButtonPosition() {
        guard let top = browserFullscreen.exitButtonTop,
              let trailing = browserFullscreen.exitButtonTrailing else { return }
        let isLandscape = view.bounds.width > view.bounds.height
        // Keep the portrait control near the top while preserving an inset on displays without a cutout.
        top.constant = isLandscape ? 8 : max(-8, 8 - view.safeAreaInsets.top)
        trailing.constant = isLandscape ? 8 : 0
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
