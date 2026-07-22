import UIKit
import WebKit

extension WKWebViewController {
    func configureReloadGesture(for targetWebView: WKWebView) {
        if let existing = targetWebView.scrollView.refreshControl {
            existing.endRefreshing()
            existing.removeTarget(nil, action: nil, for: .allEvents)
            targetWebView.scrollView.refreshControl = nil
        }

        // UIRefreshControl can leave sticky insets that clip the page header after reload.
        targetWebView.scrollView.contentInset = .zero
        targetWebView.scrollView.scrollIndicatorInsets = .zero

        guard enableReloadGesture, !disableOverscroll else {
            pendingReloadFromGesture = false
            reloadGestureArmedPullDistance = 0
            return
        }

        targetWebView.scrollView.alwaysBounceVertical = true
        let refreshControl = UIRefreshControl()
        refreshControl.addTarget(self, action: #selector(handleReloadGesture(_:)), for: .valueChanged)
        targetWebView.scrollView.refreshControl = refreshControl

        if !reloadPanObserverInstalled {
            targetWebView.scrollView.panGestureRecognizer.addTarget(
                self,
                action: #selector(handleReloadPanGesture(_:))
            )
            reloadPanObserverInstalled = true
        }
    }

    @objc func handleReloadGesture(_: UIRefreshControl) {
        guard let scrollView = self.capableWebView?.scrollView else {
            performReloadFromGesture()
            return
        }

        // Defer until finger leaves so a pull past threshold then cancel does not reload.
        if ReloadGestureSupport.shouldDeferReloadUntilTouchEnd(
            isTracking: scrollView.isTracking,
            isDragging: scrollView.isDragging
        ) {
            pendingReloadFromGesture = true
            reloadGestureArmedPullDistance = ReloadGestureSupport.pullDistance(
                contentOffsetY: scrollView.contentOffset.y,
                adjustedContentInsetTop: scrollView.adjustedContentInset.top
            )
            return
        }

        performReloadFromGesture()
    }

    @objc func handleReloadPanGesture(_ gesture: UIPanGestureRecognizer) {
        guard enableReloadGesture else { return }
        guard let scrollView = self.capableWebView?.scrollView else { return }

        switch gesture.state {
        case .ended:
            let pullDistance = ReloadGestureSupport.pullDistance(
                contentOffsetY: scrollView.contentOffset.y,
                adjustedContentInsetTop: scrollView.adjustedContentInset.top
            )
            let shouldReload = ReloadGestureSupport.shouldReloadOnTouchEnd(
                pendingReload: pendingReloadFromGesture,
                currentPullDistance: pullDistance,
                armedPullDistance: reloadGestureArmedPullDistance
            )
            pendingReloadFromGesture = false
            reloadGestureArmedPullDistance = 0

            if shouldReload {
                performReloadFromGesture()
            } else if !reloadFromGestureInProgress,
                      scrollView.refreshControl?.isRefreshing == true {
                scrollView.refreshControl?.endRefreshing()
                hardResetReloadGestureScroll(on: scrollView)
            }

        case .cancelled, .failed:
            pendingReloadFromGesture = false
            reloadGestureArmedPullDistance = 0
            guard !reloadFromGestureInProgress else { break }
            if scrollView.refreshControl?.isRefreshing == true {
                scrollView.refreshControl?.endRefreshing()
                hardResetReloadGestureScroll(on: scrollView)
            }

        default:
            break
        }
    }

    private func performReloadFromGesture() {
        pendingReloadFromGesture = false
        reloadGestureArmedPullDistance = 0
        reloadFromGestureInProgress = true

        guard let webView = self.capableWebView else {
            stopReloadGesture()
            return
        }

        // `reload()` preserves broken scroll/inset state after UIRefreshControl.
        // A fresh URL load matches a normal navigation and resets scroll like the reporter's "proper" reload.
        if let url = webView.url {
            reloadGestureNavigation = webView.load(URLRequest(url: url))
        } else {
            reloadGestureNavigation = webView.reload()
        }

        if reloadGestureNavigation == nil {
            stopReloadGesture()
        }
    }

    private func hardResetReloadGestureScroll(on scrollView: UIScrollView) {
        scrollView.contentInset = .zero
        scrollView.scrollIndicatorInsets = .zero
        let resetY = ReloadGestureSupport.contentOffsetYAfterReloadReset(
            currentY: scrollView.contentOffset.y,
            adjustedContentInsetTop: 0,
            forceToRestingTop: true
        )
        if scrollView.contentOffset.y != resetY || scrollView.contentOffset.x != 0 {
            scrollView.setContentOffset(CGPoint(x: 0, y: resetY), animated: false)
        }
    }

    func stopReloadGesture(for navigation: WKNavigation? = nil) {
        guard ReloadGestureSupport.shouldApplyStopReloadGesture(
            reloadFromGestureInProgress: reloadFromGestureInProgress
        ) else { return }

        if let expected = reloadGestureNavigation,
           let navigation,
           expected !== navigation {
            return
        }

        pendingReloadFromGesture = false
        reloadFromGestureInProgress = false
        reloadGestureNavigation = nil
        reloadGestureArmedPullDistance = 0

        guard let activeWebView = self.capableWebView else { return }
        activeWebView.scrollView.refreshControl?.endRefreshing()

        // Reset after the current runloop so WKWebView finishes applying navigation scroll state.
        DispatchQueue.main.async { [weak self] in
            guard let self, let activeWebView = self.capableWebView else { return }
            let scrollView = activeWebView.scrollView
            scrollView.refreshControl?.endRefreshing()
            self.hardResetReloadGestureScroll(on: scrollView)
            self.configureReloadGesture(for: activeWebView)
            self.hardResetReloadGestureScroll(on: activeWebView.scrollView)
            activeWebView.evaluateJavaScript("window.scrollTo(0, 0);", completionHandler: nil)

            // One more pass after layout / JS scroll restoration.
            DispatchQueue.main.async { [weak self] in
                guard let self, let activeWebView = self.capableWebView else { return }
                self.hardResetReloadGestureScroll(on: activeWebView.scrollView)
            }
        }
    }
}
