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

    @objc func handleReloadGesture(_ sender: UIRefreshControl) {
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
            return
        }

        performReloadFromGesture()
    }

    @objc func handleReloadPanGesture(_ gesture: UIPanGestureRecognizer) {
        guard enableReloadGesture else { return }
        guard let scrollView = self.capableWebView?.scrollView else { return }

        switch gesture.state {
        case .ended:
            let pastThreshold = ReloadGestureSupport.isPullPastRefreshThreshold(
                contentOffsetY: scrollView.contentOffset.y,
                adjustedContentInsetTop: scrollView.adjustedContentInset.top
            )
            let shouldReload = ReloadGestureSupport.shouldReloadOnTouchEnd(
                pendingReload: pendingReloadFromGesture,
                isPullPastThreshold: pastThreshold
            )
            pendingReloadFromGesture = false

            if shouldReload {
                performReloadFromGesture()
            } else if scrollView.refreshControl?.isRefreshing == true {
                scrollView.refreshControl?.endRefreshing()
                resetReloadGestureScrollState(on: scrollView)
            }

        case .cancelled, .failed:
            // System interruption — never commit a reload.
            pendingReloadFromGesture = false
            if scrollView.refreshControl?.isRefreshing == true {
                scrollView.refreshControl?.endRefreshing()
                resetReloadGestureScrollState(on: scrollView)
            }

        default:
            break
        }
    }

    private func performReloadFromGesture() {
        pendingReloadFromGesture = false
        reloadFromGestureInProgress = true
        reload()
    }

    private func resetReloadGestureScrollState(on scrollView: UIScrollView) {
        // Keep baseline/safe-area insets; only clamp sticky refresh overscroll.
        let resetY = ReloadGestureSupport.contentOffsetYAfterReloadReset(
            currentY: scrollView.contentOffset.y,
            adjustedContentInsetTop: scrollView.adjustedContentInset.top
        )
        if resetY != scrollView.contentOffset.y {
            scrollView.setContentOffset(CGPoint(x: scrollView.contentOffset.x, y: resetY), animated: false)
        }
    }

    func stopReloadGesture() {
        pendingReloadFromGesture = false
        let shouldResetScroll = reloadFromGestureInProgress
        reloadFromGestureInProgress = false

        guard let activeWebView = self.capableWebView else { return }
        let scrollView = activeWebView.scrollView
        scrollView.refreshControl?.endRefreshing()

        guard shouldResetScroll else { return }

        // UIRefreshControl + WKWebView.reload() often leave a top gap and block the next pull
        // until a full document navigation recreates scroll state.
        DispatchQueue.main.async { [weak self] in
            guard let self, let activeWebView = self.capableWebView else { return }
            let scrollView = activeWebView.scrollView
            scrollView.refreshControl?.endRefreshing()
            self.resetReloadGestureScrollState(on: scrollView)
            self.configureReloadGesture(for: activeWebView)
        }
    }
}
