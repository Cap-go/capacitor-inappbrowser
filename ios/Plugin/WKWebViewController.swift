//
//  WKWebViewController.swift
//  Sample
//
//  Created by Meniny on 2018-01-20.
//  Copyright © 2018年 Meniny. All rights reserved.
//

import UIKit
import WebKit

private let estimatedProgressKeyPath = "estimatedProgress"
private let titleKeyPath = "title"
private let cookieKey = "Cookie"

private struct UrlsHandledByApp {
    static var hosts = ["itunes.apple.com"]
    static var schemes = ["tel", "mailto", "sms"]
    static var blank = true
}

public struct WKWebViewCredentials {
    var username: String
    var password: String
}

@objc public protocol WKWebViewControllerDelegate {
    @objc optional func webViewController(_ controller: WKWebViewController, canDismiss url: URL) -> Bool

    @objc optional func webViewController(_ controller: WKWebViewController, didStart url: URL)
    @objc optional func webViewController(_ controller: WKWebViewController, didFinish url: URL)
    @objc optional func webViewController(_ controller: WKWebViewController, didFail url: URL, withError error: Error)
    @objc optional func webViewController(_ controller: WKWebViewController, decidePolicy url: URL, navigationType: NavigationType) -> Bool
}

extension Dictionary {
    func mapKeys<T>(_ transform: (Key) throws -> T) rethrows -> [T: Value] {
        var dictionary = [T: Value]()
        for (key, value) in self {
            dictionary[try transform(key)] = value
        }
        return dictionary
    }
}

open class WKWebViewController: UIViewController, WKScriptMessageHandler {

    public init() {
        super.init(nibName: nil, bundle: nil)
    }

    public required init?(coder aDecoder: NSCoder) {
        super.init(coder: aDecoder)
    }

    public init(source: WKWebSource?, credentials: WKWebViewCredentials? = nil) {
        super.init(nibName: nil, bundle: nil)
        self.source = source
        self.credentials = credentials
        self.initWebview()
    }

    public init(url: URL, credentials: WKWebViewCredentials? = nil) {
        super.init(nibName: nil, bundle: nil)
        self.source = .remote(url)
        self.credentials = credentials
        self.initWebview()
    }

    public init(url: URL, headers: [String: String], isInspectable: Bool, credentials: WKWebViewCredentials? = nil, preventDeeplink: Bool) {
        super.init(nibName: nil, bundle: nil)
        self.source = .remote(url)
        self.credentials = credentials
        self.setHeaders(headers: headers)
        self.setPreventDeeplink(preventDeeplink: preventDeeplink)
        self.initWebview(isInspectable: isInspectable)
    }

    public init(url: URL, headers: [String: String], isInspectable: Bool, credentials: WKWebViewCredentials? = nil, preventDeeplink: Bool, blankNavigationTab: Bool) {
        super.init(nibName: nil, bundle: nil)
        self.blankNavigationTab = blankNavigationTab
        self.source = .remote(url)
        self.credentials = credentials
        self.setHeaders(headers: headers)
        self.setPreventDeeplink(preventDeeplink: preventDeeplink)
        self.initWebview(isInspectable: isInspectable)
    }

    open var hasDynamicTitle = false
    open var source: WKWebSource?
    /// use `source` instead
    open internal(set) var url: URL?
    open var tintColor: UIColor?
    open var allowsFileURL = true
    open var delegate: WKWebViewControllerDelegate?
    open var bypassedSSLHosts: [String]?
    open var cookies: [HTTPCookie]?
    open var headers: [String: String]?
    open var capBrowserPlugin: InAppBrowserPlugin?
    var shareDisclaimer: [String: Any]?
    var shareSubject: String?
    var didpageInit = false
    var viewHeightLandscape: CGFloat?
    var viewHeightPortrait: CGFloat?
    var currentViewHeight: CGFloat?
    open var closeModal = false
    open var closeModalTitle = ""
    open var closeModalDescription = ""
    open var closeModalOk = ""
    open var closeModalCancel = ""
    open var ignoreUntrustedSSLError = false
    var viewWasPresented = false
    var preventDeeplink: Bool = false
    var blankNavigationTab: Bool = false
    var capacitorStatusBar: UIView?

    internal var preShowSemaphore: DispatchSemaphore?
    internal var preShowError: String?

    func setHeaders(headers: [String: String]) {
        self.headers = headers
        let lowercasedHeaders = headers.mapKeys { $0.lowercased() }
        let userAgent = lowercasedHeaders["user-agent"]
        self.headers?.removeValue(forKey: "User-Agent")
        self.headers?.removeValue(forKey: "user-agent")

        if let userAgent = userAgent {
            self.customUserAgent = userAgent
        }
    }

    func setPreventDeeplink(preventDeeplink: Bool) {
        self.preventDeeplink = preventDeeplink
    }

    internal var customUserAgent: String? {
        didSet {
            guard let agent = userAgent else {
                return
            }
            webView?.customUserAgent = agent
        }
    }

    open var userAgent: String? {
        didSet {
            guard let originalUserAgent = originalUserAgent, let userAgent = userAgent else {
                return
            }
            webView?.customUserAgent = [originalUserAgent, userAgent].joined(separator: " ")
        }
    }

    open var pureUserAgent: String? {
        didSet {
            guard let agent = pureUserAgent else {
                return
            }
            webView?.customUserAgent = agent
        }
    }

    open var websiteTitleInNavigationBar = true
    open var doneBarButtonItemPosition: NavigationBarPosition = .right
    open var showArrowAsClose = false
    open var preShowScript: String?
    open var leftNavigationBarItemTypes: [BarButtonItemType] = []
    open var rightNavigaionBarItemTypes: [BarButtonItemType] = []

    // Status bar style to be applied
    open var statusBarStyle: UIStatusBarStyle = .default

    // Status bar background view
    private var statusBarBackgroundView: UIView?

    // Status bar height
    private var statusBarHeight: CGFloat {
        return UIApplication.shared.windows.first?.windowScene?.statusBarManager?.statusBarFrame.height ?? 0
    }

    // Make status bar background with colored view underneath
    open func setupStatusBarBackground(color: UIColor) {
        // Remove any existing status bar view
        statusBarBackgroundView?.removeFromSuperview()

        // Create a new view to cover both status bar and navigation bar
        statusBarBackgroundView = UIView()

        if let navView = navigationController?.view {
            // Add to back of view hierarchy
            navView.insertSubview(statusBarBackgroundView!, at: 0)
            statusBarBackgroundView?.translatesAutoresizingMaskIntoConstraints = false

            // Calculate total height - status bar + navigation bar
            let navBarHeight = navigationController?.navigationBar.frame.height ?? 44
            let totalHeight = statusBarHeight + navBarHeight

            // Position from top of screen to bottom of navigation bar
            NSLayoutConstraint.activate([
                statusBarBackgroundView!.topAnchor.constraint(equalTo: navView.topAnchor),
                statusBarBackgroundView!.leadingAnchor.constraint(equalTo: navView.leadingAnchor),
                statusBarBackgroundView!.trailingAnchor.constraint(equalTo: navView.trailingAnchor),
                statusBarBackgroundView!.heightAnchor.constraint(equalToConstant: totalHeight)
            ])

            // Set background color
            statusBarBackgroundView?.backgroundColor = color

            // Make navigation bar transparent to show our view underneath
            navigationController?.navigationBar.setBackgroundImage(UIImage(), for: .default)
            navigationController?.navigationBar.shadowImage = UIImage()
            navigationController?.navigationBar.isTranslucent = true
        }
    }

    // Override to use our custom status bar style
    override open var preferredStatusBarStyle: UIStatusBarStyle {
        return statusBarStyle
    }

    // Force status bar style update when needed
    open func updateStatusBarStyle() {
        setNeedsStatusBarAppearanceUpdate()
    }

    open var backBarButtonItemImage: UIImage?
    open var forwardBarButtonItemImage: UIImage?
    open var reloadBarButtonItemImage: UIImage?
    open var stopBarButtonItemImage: UIImage?
    open var activityBarButtonItemImage: UIImage?

    open var buttonNearDoneIcon: UIImage?

    fileprivate var webView: WKWebView?
    fileprivate var progressView: UIProgressView?

    fileprivate var previousNavigationBarState: (tintColor: UIColor, hidden: Bool) = (.black, false)
    fileprivate var previousToolbarState: (tintColor: UIColor, hidden: Bool) = (.black, false)

    fileprivate var originalUserAgent: String?

    fileprivate lazy var backBarButtonItem: UIBarButtonItem = {
        let navBackImage = UIImage(systemName: "chevron.backward")?.withRenderingMode(.alwaysTemplate)
        let barButtonItem = UIBarButtonItem(image: navBackImage, style: .plain, target: self, action: #selector(backDidClick(sender:)))
        if let tintColor = self.tintColor ?? self.navigationController?.navigationBar.tintColor {
            barButtonItem.tintColor = tintColor
        }
        return barButtonItem
    }()

    fileprivate lazy var forwardBarButtonItem: UIBarButtonItem = {
        let forwardImage = UIImage(systemName: "chevron.forward")?.withRenderingMode(.alwaysTemplate)
        let barButtonItem = UIBarButtonItem(image: forwardImage, style: .plain, target: self, action: #selector(forwardDidClick(sender:)))
        if let tintColor = self.tintColor ?? self.navigationController?.navigationBar.tintColor {
            barButtonItem.tintColor = tintColor
        }
        return barButtonItem
    }()

    fileprivate lazy var reloadBarButtonItem: UIBarButtonItem = {
        if let image = reloadBarButtonItemImage {
            return UIBarButtonItem(image: image, style: .plain, target: self, action: #selector(reloadDidClick(sender:)))
        } else {
            return UIBarButtonItem(barButtonSystemItem: .refresh, target: self, action: #selector(reloadDidClick(sender:)))
        }
    }()

    fileprivate lazy var stopBarButtonItem: UIBarButtonItem = {
        if let image = stopBarButtonItemImage {
            return UIBarButtonItem(image: image, style: .plain, target: self, action: #selector(stopDidClick(sender:)))
        } else {
            return UIBarButtonItem(barButtonSystemItem: .stop, target: self, action: #selector(stopDidClick(sender:)))
        }
    }()

    fileprivate lazy var activityBarButtonItem: UIBarButtonItem = {
        // Check if custom image is provided
        if let image = activityBarButtonItemImage {
            let button = UIBarButtonItem(image: image.withRenderingMode(.alwaysTemplate),
                                            style: .plain,
                                            target: self,
                                            action: #selector(activityDidClick(sender:)))

            // Apply tint from navigation bar or from tintColor property
            if let tintColor = self.tintColor ?? self.navigationController?.navigationBar.tintColor {
                button.tintColor = tintColor
            }

            print("[DEBUG] Created activity button with custom image")
            return button
        } else {
            // Use system share icon
            let button = UIBarButtonItem(barButtonSystemItem: .action,
                                            target: self,
                                            action: #selector(activityDidClick(sender:)))

            // Apply tint from navigation bar or from tintColor property
            if let tintColor = self.tintColor ?? self.navigationController?.navigationBar.tintColor {
                button.tintColor = tintColor
            }

            print("[DEBUG] Created activity button with system action icon")
            return button
        }
    }()

    fileprivate lazy var doneBarButtonItem: UIBarButtonItem = {
        if showArrowAsClose {
            // Show chevron icon when showArrowAsClose is true (originally was arrow.left)
            let chevronImage = UIImage(systemName: "chevron.left")?.withRenderingMode(.alwaysTemplate)
            let barButtonItem = UIBarButtonItem(image: chevronImage, style: .plain, target: self, action: #selector(doneDidClick(sender:)))
            if let tintColor = self.tintColor ?? self.navigationController?.navigationBar.tintColor {
                barButtonItem.tintColor = tintColor
            }
            return barButtonItem
        } else {
            // Show X icon by default
            let xImage = UIImage(systemName: "xmark")?.withRenderingMode(.alwaysTemplate)
            let barButtonItem = UIBarButtonItem(image: xImage, style: .plain, target: self, action: #selector(doneDidClick(sender:)))
            if let tintColor = self.tintColor ?? self.navigationController?.navigationBar.tintColor {
                barButtonItem.tintColor = tintColor
            }
            return barButtonItem
        }
    }()

    fileprivate lazy var flexibleSpaceBarButtonItem: UIBarButtonItem = {
        return UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil)
    }()

    fileprivate var credentials: WKWebViewCredentials?

    var textZoom: Int?

    var capableWebView: WKWebView? {
        return webView
    }

    deinit {
        webView?.removeObserver(self, forKeyPath: estimatedProgressKeyPath)
        if websiteTitleInNavigationBar {
            webView?.removeObserver(self, forKeyPath: titleKeyPath)
        }
        webView?.removeObserver(self, forKeyPath: #keyPath(WKWebView.url))
    }

    override open func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)

        if let capacitorStatusBar = capacitorStatusBar {
            self.capBrowserPlugin?.bridge?.webView?.superview?.addSubview(capacitorStatusBar)
            self.capBrowserPlugin?.bridge?.webView?.frame.origin.y = capacitorStatusBar.frame.height
        }
    }

    override open func viewDidLoad() {
        super.viewDidLoad()
        if self.webView == nil {
            self.initWebview()
        }

        // Force all buttons to use tint color
        updateButtonTintColors()

        // Extra call to ensure buttonNearDone is visible
        if buttonNearDoneIcon != nil {
            // Delay slightly to ensure navigation items are set up
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { [weak self] in
                self?.updateButtonTintColors()

                // Force update UI if needed
                self?.navigationController?.navigationBar.setNeedsLayout()
            }
        }
    }

    func updateButtonTintColors() {
        // Ensure all button items use the navigation bar's tint color
        if let tintColor = navigationController?.navigationBar.tintColor {
            backBarButtonItem.tintColor = tintColor
            forwardBarButtonItem.tintColor = tintColor
            reloadBarButtonItem.tintColor = tintColor
            stopBarButtonItem.tintColor = tintColor
            activityBarButtonItem.tintColor = tintColor
            doneBarButtonItem.tintColor = tintColor

            // Update navigation items
            if let leftItems = navigationItem.leftBarButtonItems {
                for item in leftItems {
                    item.tintColor = tintColor
                }
            }

            if let rightItems = navigationItem.rightBarButtonItems {
                for item in rightItems {
                    item.tintColor = tintColor
                }
            }

            // Create buttonNearDone button with the correct tint color if it doesn't already exist
            if buttonNearDoneIcon != nil &&
                navigationItem.rightBarButtonItems?.count == 1 &&
                navigationItem.rightBarButtonItems?.first == doneBarButtonItem {

                // Create a properly tinted button
                let buttonItem = UIBarButtonItem(image: buttonNearDoneIcon?.withRenderingMode(.alwaysTemplate),
                                                    style: .plain,
                                                    target: self,
                                                    action: #selector(buttonNearDoneDidClick))
                buttonItem.tintColor = tintColor

                // Add it to right items
                navigationItem.rightBarButtonItems?.append(buttonItem)
            }
        }
    }

    override open func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)

        // Update colors when appearance changes
        if traitCollection.hasDifferentColorAppearance(comparedTo: previousTraitCollection) {
            // Update tint colors
            let isDarkMode = traitCollection.userInterfaceStyle == .dark
            let textColor = isDarkMode ? UIColor.white : UIColor.black

            if let navBar = navigationController?.navigationBar {
                if navBar.backgroundColor == UIColor.black || navBar.backgroundColor == UIColor.white {
                    navBar.backgroundColor = isDarkMode ? UIColor.black : UIColor.white
                    navBar.tintColor = textColor
                    navBar.titleTextAttributes = [NSAttributedString.Key.foregroundColor: textColor]

                    // Update all buttons
                    updateButtonTintColors()
                }
            }
        }
    }

    open func setCredentials(credentials: WKWebViewCredentials?) {
        self.credentials = credentials
    }

    // Method to send a message from Swift to JavaScript
    open func postMessageToJS(message: [String: Any]) {
        if let jsonData = try? JSONSerialization.data(withJSONObject: message, options: []),
            let jsonString = String(data: jsonData, encoding: .utf8) {
            let script = "window.dispatchEvent(new CustomEvent('messageFromNative', { detail: \(jsonString) }));"
            DispatchQueue.main.async {
                self.webView?.evaluateJavaScript(script, completionHandler: nil)
            }
        }
    }

    // Method to receive messages from JavaScript
    public func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        if message.name == "messageHandler" {
            if let messageBody = message.body as? [String: Any] {
                print("Received message from JavaScript:", messageBody)
                self.capBrowserPlugin?.notifyListeners("messageFromWebview", data: messageBody)
            } else {
                print("Received non-dictionary message from JavaScript:", message.body)
                self.capBrowserPlugin?.notifyListeners("messageFromWebview", data: ["rawMessage": String(describing: message.body)])
            }
        } else if message.name == "preShowScriptSuccess" {
            guard let semaphore = preShowSemaphore else {
                print("[InAppBrowser - preShowScriptSuccess]: Semaphore not found")
                return
            }

            semaphore.signal()
        } else if message.name == "preShowScriptError" {
            guard let semaphore = preShowSemaphore else {
                print("[InAppBrowser - preShowScriptError]: Semaphore not found")
                return
            }
            print("[InAppBrowser - preShowScriptError]: Error!!!!")
            semaphore.signal()
        } else if message.name == "close" {
            closeView()
		} else if message.name == "magicPrint" {
			if let webView = self.webView {
				let printController = UIPrintInteractionController.shared

				let printInfo = UIPrintInfo(dictionary: nil)
				printInfo.outputType = .general
				printInfo.jobName = "Print Job"

				printController.printInfo = printInfo
				printController.printFormatter = webView.viewPrintFormatter()

				printController.present(animated: true, completionHandler: nil)
			}
		}
    }

    func injectJavaScriptInterface() {
        let script = """
                if (!window.mobileApp) {
                        window.mobileApp = {
                                postMessage: function(message) {
                                        if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.messageHandler) {
                                                window.webkit.messageHandlers.messageHandler.postMessage(message);
                                        }
                                },
                                close: function() {
                                        window.webkit.messageHandlers.close.postMessage(null);
                                }
                        };
                }
                """
		DispatchQueue.main.async {
			self.webView?.evaluateJavaScript(script) { result, error in
				if let error = error {
					print("JavaScript evaluation error: \(error)")
				} else if let result = result {
					print("JavaScript result: \(result)")
				} else {
					print("JavaScript executed with no result")
				}
			}
		}
    }

    open func initWebview(isInspectable: Bool = true) {
        self.view.backgroundColor = UIColor.white

        self.extendedLayoutIncludesOpaqueBars = true
        self.edgesForExtendedLayout = [.bottom]

        let webConfiguration = WKWebViewConfiguration()
        let userContentController = WKUserContentController()
        userContentController.add(self, name: "messageHandler")
        userContentController.add(self, name: "preShowScriptError")
        userContentController.add(self, name: "preShowScriptSuccess")
        userContentController.add(self, name: "close")
		 userContentController.add(self, name: "magicPrint")

		// Inject JavaScript to override window.print
		let script = WKUserScript(
			source: """
			window.print = function() {
				window.webkit.messageHandlers.magicPrint.postMessage('magicPrint');
			};
			""",
			injectionTime: .atDocumentStart,
			forMainFrameOnly: false
		)
		userContentController.addUserScript(script)

        webConfiguration.allowsInlineMediaPlayback = true
        webConfiguration.userContentController = userContentController

        let webView = WKWebView(frame: .zero, configuration: webConfiguration)

//        if webView.responds(to: Selector(("setInspectable:"))) {
//            // Fix: https://stackoverflow.com/questions/76216183/how-to-debug-wkwebview-in-ios-16-4-1-using-xcode-14-2/76603043#76603043
//            webView.perform(Selector(("setInspectable:")), with: isInspectable)
//        }

		if #available(iOS 16.4, *) {
			webView.isInspectable = true
		} else {
			// Fallback on earlier versions
		}

        if self.blankNavigationTab {
            // First add the webView to view hierarchy
            self.view.addSubview(webView)

            // Then set up constraints
            webView.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                webView.topAnchor.constraint(equalTo: self.view.safeAreaLayoutGuide.topAnchor),
                webView.leadingAnchor.constraint(equalTo: self.view.leadingAnchor),
                webView.trailingAnchor.constraint(equalTo: self.view.trailingAnchor),
                webView.bottomAnchor.constraint(equalTo: self.view.bottomAnchor)
            ])
        }

        webView.uiDelegate = self
        webView.navigationDelegate = self

        webView.allowsBackForwardNavigationGestures = true
        webView.isMultipleTouchEnabled = true

        webView.addObserver(self, forKeyPath: estimatedProgressKeyPath, options: .new, context: nil)
        if websiteTitleInNavigationBar {
            webView.addObserver(self, forKeyPath: titleKeyPath, options: .new, context: nil)
        }
        webView.addObserver(self, forKeyPath: #keyPath(WKWebView.url), options: .new, context: nil)

        if !self.blankNavigationTab {
            self.view = webView
        }
        self.webView = webView

        self.webView?.customUserAgent = self.customUserAgent ?? self.userAgent ?? self.originalUserAgent

        self.navigationItem.title = self.navigationItem.title ?? self.source?.absoluteString

        if let navigation = self.navigationController {
            self.previousNavigationBarState = (navigation.navigationBar.tintColor, navigation.navigationBar.isHidden)
            self.previousToolbarState = (navigation.toolbar.tintColor, navigation.toolbar.isHidden)
        }

        if let s = self.source {
            self.load(source: s)
        } else {
            print("[\(type(of: self))][Error] Invalid url")
        }
    }

    open func setupViewElements() {
        self.setUpProgressView()
        self.setUpConstraints()
        self.setUpNavigationBarAppearance()
        self.addBarButtonItems()
        self.updateBarButtonItems()
    }

    @objc func restateViewHeight() {
        var bottomPadding = CGFloat(0.0)
        var topPadding = CGFloat(0.0)
        if #available(iOS 11.0, *) {
            let window = UIApplication.shared.windows.first(where: { $0.isKeyWindow })
            bottomPadding = window?.safeAreaInsets.bottom ?? 0.0
            topPadding = window?.safeAreaInsets.top ?? 0.0
        }
        if UIDevice.current.orientation.isPortrait {
            // Don't force toolbar visibility
            if self.viewHeightPortrait == nil {
                self.viewHeightPortrait = self.view.safeAreaLayoutGuide.layoutFrame.size.height
                self.viewHeightPortrait! += bottomPadding
                if self.navigationController?.navigationBar.isHidden == true {
                    self.viewHeightPortrait! += topPadding
                }
            }
            self.currentViewHeight = self.viewHeightPortrait
        } else if UIDevice.current.orientation.isLandscape {
            // Don't force toolbar visibility
            if self.viewHeightLandscape == nil {
                self.viewHeightLandscape = self.view.safeAreaLayoutGuide.layoutFrame.size.height
                self.viewHeightLandscape! += bottomPadding
                if self.navigationController?.navigationBar.isHidden == true {
                    self.viewHeightLandscape! += topPadding
                }
            }
            self.currentViewHeight = self.viewHeightLandscape
        }
    }

    override open func viewWillTransition(to size: CGSize, with coordinator: UIViewControllerTransitionCoordinator) {
        //        self.view.frame.size.height = self.currentViewHeight!
    }

    override open func viewWillLayoutSubviews() {
        restateViewHeight()
        if self.currentViewHeight != nil {
            self.view.frame.size.height = self.currentViewHeight!
        }
    }

    override open func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        if !self.viewWasPresented {
            self.setupViewElements()
            setUpState()
            self.viewWasPresented = true
        }

        // Force update button appearances
        updateButtonTintColors()

        // Ensure status bar appearance is correct when view appears
        // Make sure we have the latest tint color
        if let tintColor = self.tintColor {
            // Update the status bar background if needed
            if let navController = navigationController, let backgroundColor = navController.navigationBar.backgroundColor ?? statusBarBackgroundView?.backgroundColor {
                setupStatusBarBackground(color: backgroundColor)
            } else {
                setupStatusBarBackground(color: UIColor.white)
            }
        }

        // Update status bar style
        updateStatusBarStyle()

        // Special handling for blank toolbar mode
        if blankNavigationTab && statusBarBackgroundView != nil {
            if let color = statusBarBackgroundView?.backgroundColor {
                // Set view color to match status bar
                view.backgroundColor = color
            }
        }
    }

    override open func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)

        // Force add buttonNearDone if it's not visible yet
        if buttonNearDoneIcon != nil {
            // Check if button already exists in the navigation bar
            let buttonExists = navigationItem.rightBarButtonItems?.contains { item in
                return item.action == #selector(buttonNearDoneDidClick)
            } ?? false

            if !buttonExists {
                // Create and add the button directly
                let buttonItem = UIBarButtonItem(
                    image: buttonNearDoneIcon?.withRenderingMode(.alwaysTemplate),
                    style: .plain,
                    target: self,
                    action: #selector(buttonNearDoneDidClick)
                )

                // Apply tint color
                if let tintColor = self.tintColor ?? self.navigationController?.navigationBar.tintColor {
                    buttonItem.tintColor = tintColor
                }

                // Add to right items
                if navigationItem.rightBarButtonItems == nil {
                    navigationItem.rightBarButtonItems = [buttonItem]
                } else {
                    var items = navigationItem.rightBarButtonItems ?? []
                    items.append(buttonItem)
                    navigationItem.rightBarButtonItems = items
                }

                print("[DEBUG] Force added buttonNearDone in viewDidAppear")
            }
        }
    }

    override open func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        rollbackState()
    }

    override open func observeValue(forKeyPath keyPath: String?, of object: Any?, change: [NSKeyValueChangeKey: Any]?, context: UnsafeMutableRawPointer?) {
        switch keyPath {
        case estimatedProgressKeyPath?:
            DispatchQueue.main.async {
                guard let estimatedProgress = self.webView?.estimatedProgress else {
                    return
                }
                self.progressView?.alpha = 1
                self.progressView?.setProgress(Float(estimatedProgress), animated: true)

                if estimatedProgress >= 1.0 {
                    UIView.animate(withDuration: 0.3, delay: 0.3, options: .curveEaseOut, animations: {
                        self.progressView?.alpha = 0
                    }, completion: {
                        _ in
                        self.progressView?.setProgress(0, animated: false)
                    })
                }
            }
        case titleKeyPath?:
            if self.hasDynamicTitle {
                self.navigationItem.title = webView?.url?.host
            }
        case "URL":

            self.capBrowserPlugin?.notifyListeners("urlChangeEvent", data: ["url": webView?.url?.absoluteString ?? ""])
            self.injectJavaScriptInterface()
        default:
            super.observeValue(forKeyPath: keyPath, of: object, change: change, context: context)
        }
    }
}

// MARK: - Public Methods
public extension WKWebViewController {

    func load(source s: WKWebSource) {
        switch s {
        case .remote(let url):
            self.load(remote: url)
        case .file(let url, access: let access):
            self.load(file: url, access: access)
        case .string(let str, base: let base):
            self.load(string: str, base: base)
        }
    }

    func load(remote: URL) {
        DispatchQueue.main.async {
            self.webView?.load(self.createRequest(url: remote))
        }
    }

    func load(file: URL, access: URL) {
        webView?.loadFileURL(file, allowingReadAccessTo: access)
    }

    func load(string: String, base: URL? = nil) {
        webView?.loadHTMLString(string, baseURL: base)
    }

    func goBackToFirstPage() {
        if let firstPageItem = webView?.backForwardList.backList.first {
            webView?.go(to: firstPageItem)
        }
    }
    func reload() {
        webView?.reload()
    }

    func executeScript(script: String, completion: ((Any?, Error?) -> Void)? = nil) {
        DispatchQueue.main.async { [weak self] in
            self?.webView?.evaluateJavaScript(script, completionHandler: completion)
        }
    }

    func applyTextZoom(_ zoomPercent: Int) {
        let script = """
        document.getElementsByTagName('body')[0].style.webkitTextSizeAdjust = '\(zoomPercent)%';
        document.getElementsByTagName('body')[0].style.textSizeAdjust = '\(zoomPercent)%';
        """

        executeScript(script: script)
    }
}

// MARK: - Fileprivate Methods
fileprivate extension WKWebViewController {
    var availableCookies: [HTTPCookie]? {
        return cookies?.filter {
            cookie in
            var result = true
            let url = self.source?.remoteURL
            if let host = url?.host, !cookie.domain.hasSuffix(host) {
                result = false
            }
            if cookie.isSecure && url?.scheme != "https" {
                result = false
            }

            return result
        }
    }
    func createRequest(url: URL) -> URLRequest {
        var request = URLRequest(url: url)

        // Set up headers
        if let headers = headers {
            for (field, value) in headers {
                request.addValue(value, forHTTPHeaderField: field)
            }
        }

        // Set up Cookies
        if let cookies = availableCookies, let value = HTTPCookie.requestHeaderFields(with: cookies)[cookieKey] {
            request.addValue(value, forHTTPHeaderField: cookieKey)
        }

        return request
    }

    func setUpProgressView() {
        let progressView = UIProgressView(progressViewStyle: .default)
        progressView.trackTintColor = UIColor(white: 1, alpha: 0)
        self.progressView = progressView
        //        updateProgressViewFrame()
    }

    func setUpConstraints() {
        if !(self.navigationController?.navigationBar.isHidden)! {
            self.progressView?.frame.origin.y = CGFloat((self.navigationController?.navigationBar.frame.height)!)
            self.navigationController?.navigationBar.addSubview(self.progressView!)
            webView?.addObserver(self, forKeyPath: #keyPath(WKWebView.estimatedProgress), options: .new, context: nil)
        }
    }

    func addBarButtonItems() {
        func barButtonItem(_ type: BarButtonItemType) -> UIBarButtonItem? {
            switch type {
            case .back:
                return backBarButtonItem
            case .forward:
                return forwardBarButtonItem
            case .reload:
                return reloadBarButtonItem
            case .stop:
                return stopBarButtonItem
            case .activity:
                return activityBarButtonItem
            case .done:
                return doneBarButtonItem
            case .flexibleSpace:
                return flexibleSpaceBarButtonItem
            case .custom(let icon, let title, let action):
                let item: BlockBarButtonItem
                if let icon = icon {
                    item = BlockBarButtonItem(image: icon, style: .plain, target: self, action: #selector(customDidClick(sender:)))
                } else {
                    item = BlockBarButtonItem(title: title, style: .plain, target: self, action: #selector(customDidClick(sender:)))
                }
                item.block = action
                return item
            }
        }

        switch doneBarButtonItemPosition {
        case .left:
            if !leftNavigationBarItemTypes.contains(where: { type in
                switch type {
                case .done:
                    return true
                default:
                    return false
                }
            }) {
                leftNavigationBarItemTypes.insert(.done, at: 0)
            }
        case .right:
            if !rightNavigaionBarItemTypes.contains(where: { type in
                switch type {
                case .done:
                    return true
                default:
                    return false
                }
            }) {
                rightNavigaionBarItemTypes.insert(.done, at: 0)
            }
        case .none:
            break
        }

        navigationItem.leftBarButtonItems = leftNavigationBarItemTypes.map {
            barButtonItemType in
            if let barButtonItem = barButtonItem(barButtonItemType) {
                return barButtonItem
            }
            return UIBarButtonItem()
        }

        var rightBarButtons = rightNavigaionBarItemTypes.map {
            barButtonItemType in
            if let barButtonItem = barButtonItem(barButtonItemType) {
                return barButtonItem
            }
            return UIBarButtonItem()
        }

        // If we have buttonNearDoneIcon and the first (or only) right button is the done button
        if buttonNearDoneIcon != nil &&
            ((rightBarButtons.count == 1 && rightBarButtons[0] == doneBarButtonItem) ||
                (rightBarButtons.isEmpty && doneBarButtonItemPosition == .right) ||
                rightBarButtons.contains(doneBarButtonItem)) {

            // Check if button already exists to avoid duplicates
            let buttonExists = rightBarButtons.contains { item in
                let selector = #selector(buttonNearDoneDidClick)
                return item.action == selector
            }

            if !buttonExists {
                // Create button with proper tint and template rendering mode
                let buttonItem = UIBarButtonItem(
                    image: buttonNearDoneIcon?.withRenderingMode(.alwaysTemplate),
                    style: .plain,
                    target: self,
                    action: #selector(buttonNearDoneDidClick)
                )

                // Apply tint from navigation bar or from tintColor property
                if let tintColor = self.tintColor ?? self.navigationController?.navigationBar.tintColor {
                    buttonItem.tintColor = tintColor
                }

                // Make sure the done button is there before adding this one
                if rightBarButtons.isEmpty && doneBarButtonItemPosition == .right {
                    rightBarButtons.append(doneBarButtonItem)
                }

                // Add the button
                rightBarButtons.append(buttonItem)

                print("[DEBUG] Added buttonNearDone to right bar buttons, icon: \(String(describing: buttonNearDoneIcon))")
            } else {
                print("[DEBUG] buttonNearDone already exists in right bar buttons")
            }
        }

        navigationItem.rightBarButtonItems = rightBarButtons

        // After all buttons are set up, apply tint color
        updateButtonTintColors()
    }

    func updateBarButtonItems() {
        // Update navigation buttons (completely separate from close button)
        backBarButtonItem.isEnabled = webView?.canGoBack ?? false
        forwardBarButtonItem.isEnabled = webView?.canGoForward ?? false

        let updateReloadBarButtonItem: (UIBarButtonItem, Bool) -> UIBarButtonItem = {
            [unowned self] barButtonItem, isLoading in
            switch barButtonItem {
            case self.reloadBarButtonItem:
                fallthrough
            case self.stopBarButtonItem:
                return isLoading ? self.stopBarButtonItem : self.reloadBarButtonItem
            default:
                break
            }
            return barButtonItem
        }

        let isLoading = webView?.isLoading ?? false
        navigationItem.leftBarButtonItems = navigationItem.leftBarButtonItems?.map {
            barButtonItem -> UIBarButtonItem in
            return updateReloadBarButtonItem(barButtonItem, isLoading)
        }

        navigationItem.rightBarButtonItems = navigationItem.rightBarButtonItems?.map {
            barButtonItem -> UIBarButtonItem in
            return updateReloadBarButtonItem(barButtonItem, isLoading)
        }
    }

    func setUpState() {
        navigationController?.setNavigationBarHidden(false, animated: true)

        // Always hide toolbar since we never want it
        navigationController?.setToolbarHidden(true, animated: true)

        // Set tint colors but don't override specific colors
        if tintColor == nil {
            // Use system appearance if no specific tint color is set
            let isDarkMode = traitCollection.userInterfaceStyle == .dark
            let textColor = isDarkMode ? UIColor.white : UIColor.black

            navigationController?.navigationBar.tintColor = textColor
            progressView?.progressTintColor = textColor
        } else {
            progressView?.progressTintColor = tintColor
            navigationController?.navigationBar.tintColor = tintColor
        }
    }

    func rollbackState() {
        progressView?.progress = 0

        navigationController?.navigationBar.tintColor = previousNavigationBarState.tintColor

        navigationController?.setNavigationBarHidden(previousNavigationBarState.hidden, animated: true)
    }

    func checkRequestCookies(_ request: URLRequest, cookies: [HTTPCookie]) -> Bool {
        if cookies.count <= 0 {
            return true
        }
        guard let headerFields = request.allHTTPHeaderFields, let cookieString = headerFields[cookieKey] else {
            return false
        }

        let requestCookies = cookieString.components(separatedBy: ";").map {
            $0.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: "=", maxSplits: 1).map(String.init)
        }

        var valid = false
        for cookie in cookies {
            valid = requestCookies.filter {
                $0[0] == cookie.name && $0[1] == cookie.value
            }.count > 0
            if !valid {
                break
            }
        }
        return valid
    }

    func openURLWithApp(_ url: URL) -> Bool {
        let application = UIApplication.shared
        if application.canOpenURL(url) {
            application.open(url, options: [:], completionHandler: nil)
            return true
        }

        return false
    }

    func handleURLWithApp(_ url: URL, targetFrame: WKFrameInfo?) -> Bool {
        // If preventDeeplink is true, don't try to open URLs in external apps
        if self.preventDeeplink {
            return false
        }

        let hosts = UrlsHandledByApp.hosts
        let schemes = UrlsHandledByApp.schemes
        let blank = UrlsHandledByApp.blank

        var tryToOpenURLWithApp = false

        // Handle all non-http(s) schemes by default
        if let scheme = url.scheme?.lowercased(), !scheme.hasPrefix("http") {
            tryToOpenURLWithApp = true
        }

        // Also handle specific hosts and schemes from UrlsHandledByApp
        if let host = url.host, hosts.contains(host) {
            tryToOpenURLWithApp = true
        }
        if let scheme = url.scheme, schemes.contains(scheme) {
            tryToOpenURLWithApp = true
        }
        if blank && targetFrame == nil {
            tryToOpenURLWithApp = true
        }

        return tryToOpenURLWithApp ? openURLWithApp(url) : false
    }

    @objc func backDidClick(sender: AnyObject) {
        // Only handle back navigation, not closing
        if webView?.canGoBack ?? false {
            webView?.goBack()
        }
    }

    @objc func forwardDidClick(sender: AnyObject) {
        webView?.goForward()
    }

    @objc func buttonNearDoneDidClick(sender: AnyObject) {
        self.capBrowserPlugin?.notifyListeners("buttonNearDoneClick", data: [:])
    }

    @objc func reloadDidClick(sender: AnyObject) {
        webView?.stopLoading()
        if webView?.url != nil {
            webView?.reload()
        } else if let s = self.source {
            self.load(source: s)
        }
    }

    @objc func stopDidClick(sender: AnyObject) {
        webView?.stopLoading()
    }

    @objc func activityDidClick(sender: AnyObject) {
        print("[DEBUG] Activity button clicked, shareSubject: \(self.shareSubject ?? "nil")")

        guard let s = self.source else {
            print("[DEBUG] Activity button: No source available")
            return
        }

        let items: [Any]
        switch s {
        case .remote(let u):
            items = [u]
        case .file(let u, access: _):
            items = [u]
        case .string(let str, base: _):
            items = [str]
        }
        showDisclaimer(items: items, sender: sender)
    }

    func showDisclaimer(items: [Any], sender: AnyObject) {
        // Show disclaimer dialog before sharing if shareDisclaimer is set
        if let disclaimer = self.shareDisclaimer, !disclaimer.isEmpty {
            // Create and show the alert
            let alert = UIAlertController(
                title: disclaimer["title"] as? String ?? "Title",
                message: disclaimer["message"] as? String ?? "Message",
                preferredStyle: UIAlertController.Style.alert)

            // Add confirm button that continues with sharing
            alert.addAction(UIAlertAction(
                title: disclaimer["confirmBtn"] as? String ?? "Confirm",
                style: UIAlertAction.Style.default,
                handler: { _ in
                    // Notify that confirm was clicked
                    self.capBrowserPlugin?.notifyListeners("confirmBtnClicked", data: nil)

                    // Show the share dialog
                    self.showShareSheet(items: items, sender: sender)
                }
            ))

            // Add cancel button
            alert.addAction(UIAlertAction(
                title: disclaimer["cancelBtn"] as? String ?? "Cancel",
                style: UIAlertAction.Style.cancel,
                handler: nil
            ))

            // Present the alert
            self.present(alert, animated: true, completion: nil)
        } else {
            // No disclaimer, directly show share sheet
            showShareSheet(items: items, sender: sender)
        }
    }

    // Separated the actual sharing functionality
    private func showShareSheet(items: [Any], sender: AnyObject) {
        let activityViewController = UIActivityViewController(activityItems: items, applicationActivities: nil)
        activityViewController.setValue(self.shareSubject ?? self.title, forKey: "subject")
        activityViewController.popoverPresentationController?.barButtonItem = (sender as! UIBarButtonItem)
        self.present(activityViewController, animated: true, completion: nil)
    }

    func closeView () {
        var canDismiss = true
        if let url = self.source?.url {
            canDismiss = delegate?.webViewController?(self, canDismiss: url) ?? true
        }
        if canDismiss {
            // Cleanup webView
            webView?.stopLoading()
            webView?.removeObserver(self, forKeyPath: estimatedProgressKeyPath)
            if websiteTitleInNavigationBar {
                webView?.removeObserver(self, forKeyPath: titleKeyPath)
            }
            webView?.removeObserver(self, forKeyPath: #keyPath(WKWebView.url))
            webView?.configuration.userContentController.removeAllUserScripts()
            webView?.configuration.userContentController.removeScriptMessageHandler(forName: "messageHandler")
            webView?.configuration.userContentController.removeScriptMessageHandler(forName: "close")
            webView?.configuration.userContentController.removeScriptMessageHandler(forName: "preShowScriptSuccess")
            webView?.configuration.userContentController.removeScriptMessageHandler(forName: "preShowScriptError")
            webView = nil

            self.capBrowserPlugin?.notifyListeners("closeEvent", data: ["url": webView?.url?.absoluteString ?? ""])
            dismiss(animated: true, completion: nil)
        }
    }

    @objc func doneDidClick(sender: AnyObject) {
        // check if closeModal is true, if true display alert before close
        if self.closeModal {
            let alert = UIAlertController(title: self.closeModalTitle, message: self.closeModalDescription, preferredStyle: UIAlertController.Style.alert)
            alert.addAction(UIAlertAction(title: self.closeModalOk, style: UIAlertAction.Style.default, handler: { _ in
                self.closeView()
            }))
            alert.addAction(UIAlertAction(title: self.closeModalCancel, style: UIAlertAction.Style.default, handler: nil))
            self.present(alert, animated: true, completion: nil)
        } else {
            self.closeView()
        }

    }

    @objc func customDidClick(sender: BlockBarButtonItem) {
        sender.block?(self)
    }

    func canRotate() {}

    func close() {
        let currentUrl = webView?.url?.absoluteString ?? ""
        dismiss(animated: true, completion: nil)
        capBrowserPlugin?.notifyListeners("closeEvent", data: ["url": currentUrl])
    }

    open func setUpNavigationBarAppearance() {
        // Set up basic bar appearance
        if let navBar = navigationController?.navigationBar {
            // Make navigation bar transparent
            navBar.setBackgroundImage(UIImage(), for: .default)
            navBar.shadowImage = UIImage()
            navBar.isTranslucent = true

            // Ensure tint colors are applied properly
            if navBar.tintColor == nil {
                navBar.tintColor = tintColor ?? .black
            }

            // Ensure text colors are set
            if navBar.titleTextAttributes == nil {
                navBar.titleTextAttributes = [NSAttributedString.Key.foregroundColor: tintColor ?? .black]
            }

            // Ensure the navigation bar buttons are properly visible
            for item in navBar.items ?? [] {
                for barButton in (item.leftBarButtonItems ?? []) + (item.rightBarButtonItems ?? []) {
                    barButton.tintColor = tintColor ?? navBar.tintColor ?? .black
                }
            }
        }

        // Force button colors to update
        updateButtonTintColors()
    }
}

// MARK: - WKUIDelegate
extension WKWebViewController: WKUIDelegate {
    public func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        // Create a strong reference to the completion handler to ensure it's called
        let strongCompletionHandler = completionHandler

        // Ensure UI updates are on the main thread
        DispatchQueue.main.async { [weak self] in
            guard let self = self else {
                // View controller was deallocated
                strongCompletionHandler()
                return
            }

            // Check if view is available and ready for presentation
            guard self.view.window != nil, !self.isBeingDismissed, !self.isMovingFromParent else {
                print("[InAppBrowser] Cannot present alert - view not in window hierarchy or being dismissed")
                strongCompletionHandler()
                return
            }

            let alertController = UIAlertController(title: nil, message: message, preferredStyle: .alert)
            alertController.addAction(UIAlertAction(title: "OK", style: .default, handler: { _ in
                strongCompletionHandler()
            }))

            // Try to present the alert
            do {
                self.present(alertController, animated: true, completion: nil)
            } catch {
                // This won't typically be triggered as present doesn't throw,
                // but adding as a safeguard
                print("[InAppBrowser] Error presenting alert: \(error)")
                strongCompletionHandler()
            }
        }
    }
}

// MARK: - WKNavigationDelegate
extension WKWebViewController: WKNavigationDelegate {
    internal func injectPreShowScript() {
        if preShowSemaphore != nil {
            return
        }

        // TODO: implement interface
        let script = """
                        async function preShowFunction() {
                        \(self.preShowScript ?? "")
                        };
                        preShowFunction().then(
                                () => window.webkit.messageHandlers.preShowScriptSuccess.postMessage({})
                        ).catch(
                                err => {
                                        console.error('Preshow error', err);
                                        window.webkit.messageHandlers.preShowScriptError.postMessage(JSON.stringify(err, Object.getOwnPropertyNames(err)));
                                }
                        )
                        """
        print("[InAppBrowser - InjectPreShowScript] PreShowScript script: \(script)")

        self.preShowSemaphore = DispatchSemaphore(value: 0)
        self.executeScript(script: script) // this will run on the main thread

        defer {
            self.preShowSemaphore = nil
            self.preShowError = nil
        }

        if self.preShowSemaphore?.wait(timeout: .now() + 10) == .timedOut {
            print("[InAppBrowser - InjectPreShowScript] PreShowScript running for over 10 seconds. The plugin will not wait any longer!")
            return
        }

        //            "async function preShowFunction() {\n" +
        //            self.preShowScript + "\n" +
        //            "};\n" +
        //            "preShowFunction().then(() => window.PreShowScriptInterface.success()).catch(err => { console.error('Preshow error', err); window.PreShowScriptInterface.error(JSON.stringify(err, Object.getOwnPropertyNames(err))) })";

    }

    public func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        updateBarButtonItems()
        self.progressView?.progress = 0
        if let u = webView.url {
            self.url = u
            delegate?.webViewController?(self, didStart: u)
        }
    }
    public func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        if !didpageInit && self.capBrowserPlugin?.isPresentAfterPageLoad == true {
            // injectPreShowScript will block, don't execute on the main thread
            if self.preShowScript != nil && !self.preShowScript!.isEmpty {
                DispatchQueue.global(qos: .userInitiated).async {
                    self.injectPreShowScript()
                    DispatchQueue.main.async { [weak self] in
                        self?.capBrowserPlugin?.presentView()
                    }
                }
            } else {
                self.capBrowserPlugin?.presentView()
            }
        } else if self.preShowScript != nil && !self.preShowScript!.isEmpty && self.capBrowserPlugin?.isPresentAfterPageLoad == true {
            DispatchQueue.global(qos: .userInitiated).async {
                self.injectPreShowScript()
            }
        }

        // Apply text zoom if set
        if let zoom = self.textZoom {
            applyTextZoom(zoom)
        }

        didpageInit = true
        updateBarButtonItems()
        self.progressView?.progress = 0
        if let url = webView.url {
            self.url = url
            delegate?.webViewController?(self, didFinish: url)
        }
        self.injectJavaScriptInterface()
        self.capBrowserPlugin?.notifyListeners("browserPageLoaded", data: [:])
    }

    public func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        updateBarButtonItems()
        self.progressView?.progress = 0
        if let url = webView.url {
            self.url = url
            delegate?.webViewController?(self, didFail: url, withError: error)
        }
        self.capBrowserPlugin?.notifyListeners("pageLoadError", data: [:])
    }

    public func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        updateBarButtonItems()
        self.progressView?.progress = 0
        if let url = webView.url {
            self.url = url
            delegate?.webViewController?(self, didFail: url, withError: error)
        }
        self.capBrowserPlugin?.notifyListeners("pageLoadError", data: [:])
    }

    public func webView(_ webView: WKWebView, didReceive challenge: URLAuthenticationChallenge, completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        if let credentials = credentials,
            challenge.protectionSpace.receivesCredentialSecurely,
            let url = webView.url, challenge.protectionSpace.host == url.host, challenge.protectionSpace.protocol == url.scheme, challenge.protectionSpace.port == url.port ?? (url.scheme == "https" ? 443 : url.scheme == "http" ? 80 : nil) {
            let urlCredential = URLCredential(user: credentials.username, password: credentials.password, persistence: .none)
            completionHandler(.useCredential, urlCredential)
        } else if let bypassedSSLHosts = bypassedSSLHosts, bypassedSSLHosts.contains(challenge.protectionSpace.host) {
            let credential = URLCredential(trust: challenge.protectionSpace.serverTrust!)
            completionHandler(.useCredential, credential)
        } else {
            guard self.ignoreUntrustedSSLError else {
                completionHandler(.performDefaultHandling, nil)
                return
            }
            /* allows to open links with self-signed certificates
                Follow Apple's guidelines https://developer.apple.com/documentation/foundation/url_loading_system/handling_an_authentication_challenge/performing_manual_server_trust_authentication
                */
            guard let serverTrust = challenge.protectionSpace.serverTrust  else {
                completionHandler(.useCredential, nil)
                return
            }
            let credential = URLCredential(trust: serverTrust)
            completionHandler(.useCredential, credential)
        }
        self.injectJavaScriptInterface()
    }

    public func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        var actionPolicy: WKNavigationActionPolicy = .allow

        if self.preventDeeplink {
            actionPolicy = .preventDeeplinkActionPolicy
        }

        guard let u = navigationAction.request.url else {
            decisionHandler(actionPolicy)
            return
        }

        // Check if the URL is an App Store URL
        if u.absoluteString.contains("apps.apple.com") {
            UIApplication.shared.open(u, options: [:], completionHandler: nil)
            // Cancel the navigation in the web view
            decisionHandler(.cancel)
            return
        }

        if !self.allowsFileURL && u.isFileURL {
            print("Cannot handle file URLs")
            decisionHandler(.cancel)
            return
        }

        if handleURLWithApp(u, targetFrame: navigationAction.targetFrame) {
            actionPolicy = .cancel
        }

        if u.host == self.source?.url?.host, let cookies = availableCookies, !checkRequestCookies(navigationAction.request, cookies: cookies) {
            self.load(remote: u)
            actionPolicy = .cancel
        }

        if let navigationType = NavigationType(rawValue: navigationAction.navigationType.rawValue), let result = delegate?.webViewController?(self, decidePolicy: u, navigationType: navigationType) {
            actionPolicy = result ? .allow : .cancel
        }
        self.injectJavaScriptInterface()
        decisionHandler(actionPolicy)
    }
}

class BlockBarButtonItem: UIBarButtonItem {

    var block: ((WKWebViewController) -> Void)?
}

extension WKNavigationActionPolicy {
    static let preventDeeplinkActionPolicy = WKNavigationActionPolicy(rawValue: WKNavigationActionPolicy.allow.rawValue + 2)!
}
