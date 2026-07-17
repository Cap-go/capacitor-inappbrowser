import UIKit
import WebKit

extension WKWebViewController {
    func configureReloadGesture(for targetWebView: WKWebView) {
        if let existing = targetWebView.scrollView.refreshControl {
            existing.endRefreshing()
            existing.removeTarget(nil, action: nil, for: .allEvents)
            targetWebView.scrollView.refreshControl = nil
        }

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
            // Capture the real activation pull distance from UIRefreshControl.
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
                resetReloadGestureScrollState(on: scrollView, forceToRestingTop: false)
            }

        case .cancelled, .failed:
            // System interruption — never commit a reload.
            pendingReloadFromGesture = false
            reloadGestureArmedPullDistance = 0
            // Don't touch an in-flight gesture reload's spinner / scroll.
            guard !reloadFromGestureInProgress else { break }
            if scrollView.refreshControl?.isRefreshing == true {
                scrollView.refreshControl?.endRefreshing()
                resetReloadGestureScrollState(on: scrollView, forceToRestingTop: false)
            }

        default:
            break
        }
    }

    private func performReloadFromGesture() {
        pendingReloadFromGesture = false
        reloadGestureArmedPullDistance = 0
        reloadFromGestureInProgress = true
        // Track this navigation so cancelled prior loads don't clear gesture state early.
        reloadGestureNavigation = self.capableWebView?.reload()
        if reloadGestureNavigation == nil {
            stopReloadGesture()
        }
    }

    private func resetReloadGestureScrollState(on scrollView: UIScrollView, forceToRestingTop: Bool) {
        let resetY = ReloadGestureSupport.contentOffsetYAfterReloadReset(
            currentY: scrollView.contentOffset.y,
            adjustedContentInsetTop: scrollView.adjustedContentInset.top,
            forceToRestingTop: forceToRestingTop
        )
        if resetY != scrollView.contentOffset.y {
            scrollView.setContentOffset(CGPoint(x: scrollView.contentOffset.x, y: resetY), animated: false)
        }
    }

    func stopReloadGesture(for navigation: WKNavigation? = nil) {
        pendingReloadFromGesture = false

        // Ignore unrelated navigation callbacks while a gesture reload is in flight.
        if reloadFromGestureInProgress,
           let expected = reloadGestureNavigation,
           let navigation,
           expected !== navigation {
            return
        }

        let shouldResetScroll = reloadFromGestureInProgress
        reloadFromGestureInProgress = false
        reloadGestureNavigation = nil
        reloadGestureArmedPullDistance = 0

        guard let activeWebView = self.capableWebView else { return }
        let scrollView = activeWebView.scrollView
        scrollView.refreshControl?.endRefreshing()

        guard shouldResetScroll else { return }

        // UIRefreshControl + WKWebView.reload() often leave offset at 0 instead of
        // -adjustedContentInset.top (clips header ~safe-area) and block the next pull.
        DispatchQueue.main.async { [weak self] in
            guard let self, let activeWebView = self.capableWebView else { return }
            let scrollView = activeWebView.scrollView
            scrollView.refreshControl?.endRefreshing()
            self.resetReloadGestureScrollState(on: scrollView, forceToRestingTop: true)
            self.configureReloadGesture(for: activeWebView)
            // Rebind can change adjusted insets; snap again to the new resting top.
            self.resetReloadGestureScrollState(
                on: activeWebView.scrollView,
                forceToRestingTop: true
            )
        }
    }
}
