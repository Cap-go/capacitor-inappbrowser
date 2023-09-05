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

open class WKWebViewController: UIViewController {

    public init() {
        super.init(nibName: nil, bundle: nil)
    }

    public required init?(coder aDecoder: NSCoder) {
        super.init(coder: aDecoder)
    }

    public init(source: WKWebSource?) {
        super.init(nibName: nil, bundle: nil)
        self.source = source
        self.initWebview()
    }

    public init(url: URL) {
        super.init(nibName: nil, bundle: nil)
        self.source = .remote(url)
        self.initWebview()
    }

    public init(url: URL, headers: [String: String]) {
        super.init(nibName: nil, bundle: nil)
        self.source = .remote(url)
        self.setHeaders(headers: headers)
        self.initWebview()
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
    open var leftNavigaionBarItemTypes: [BarButtonItemType] = []
    open var rightNavigaionBarItemTypes: [BarButtonItemType] = []
    open var toolbarItemTypes: [BarButtonItemType] = [.back, .forward, .reload, .activity]

    open var backBarButtonItemImage: UIImage?
    open var forwardBarButtonItemImage: UIImage?
    open var reloadBarButtonItemImage: UIImage?
    open var stopBarButtonItemImage: UIImage?
    open var activityBarButtonItemImage: UIImage?

    fileprivate var webView: WKWebView?
    fileprivate var progressView: UIProgressView?

    fileprivate var previousNavigationBarState: (tintColor: UIColor, hidden: Bool) = (.black, false)
    fileprivate var previousToolbarState: (tintColor: UIColor, hidden: Bool) = (.black, false)

    fileprivate var originalUserAgent: String?

    fileprivate lazy var backBarButtonItem: UIBarButtonItem = {
        let bundle = Bundle(for: WKWebViewController.self)
        return UIBarButtonItem(image: backBarButtonItemImage ?? UIImage(named: "Back", in: bundle, compatibleWith: nil), style: .plain, target: self, action: #selector(backDidClick(sender:)))
    }()

    fileprivate lazy var forwardBarButtonItem: UIBarButtonItem = {
        let bundle = Bundle(for: WKWebViewController.self)
        return UIBarButtonItem(image: forwardBarButtonItemImage ?? UIImage(named: "Forward", in: bundle, compatibleWith: nil), style: .plain, target: self, action: #selector(forwardDidClick(sender:)))
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
        if let image = activityBarButtonItemImage {
            return UIBarButtonItem(image: image, style: .plain, target: self, action: #selector(activityDidClick(sender:)))
        } else {
            return UIBarButtonItem(barButtonSystemItem: .action, target: self, action: #selector(activityDidClick(sender:)))
        }
    }()

    fileprivate lazy var doneBarButtonItem: UIBarButtonItem = {
        return UIBarButtonItem(barButtonSystemItem: .done, target: self, action: #selector(doneDidClick(sender:)))
    }()

    fileprivate lazy var flexibleSpaceBarButtonItem: UIBarButtonItem = {
        return UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil)
    }()

    deinit {
        webView?.removeObserver(self, forKeyPath: estimatedProgressKeyPath)
        if websiteTitleInNavigationBar {
            webView?.removeObserver(self, forKeyPath: titleKeyPath)
        }
        webView?.removeObserver(self, forKeyPath: #keyPath(WKWebView.url))
    }

    override open func viewDidLoad() {
        super.viewDidLoad()
        if self.webView == nil {
            self.initWebview()
        }
    }

    open func initWebview() {

        self.view.backgroundColor = UIColor.white

        self.extendedLayoutIncludesOpaqueBars = true
        self.edgesForExtendedLayout = [.bottom]

        let webConfiguration = WKWebViewConfiguration()
        let webView = WKWebView(frame: .zero, configuration: webConfiguration)

        webView.uiDelegate = self
        webView.navigationDelegate = self

        webView.allowsBackForwardNavigationGestures = true
        webView.isMultipleTouchEnabled = true

        webView.addObserver(self, forKeyPath: estimatedProgressKeyPath, options: .new, context: nil)
        if websiteTitleInNavigationBar {
            webView.addObserver(self, forKeyPath: titleKeyPath, options: .new, context: nil)
        }
        webView.addObserver(self, forKeyPath: #keyPath(WKWebView.url), options: .new, context: nil)
        //        NotificationCenter.default.addObserver(self, selector: #selector(restateViewHeight), name: UIDevice.orientationDidChangeNotification, object: nil)

        self.view = webView
        self.webView = webView

        self.webView?.customUserAgent = self.customUserAgent ?? self.userAgent ?? self.originalUserAgent

        self.navigationItem.title = self.navigationItem.title ?? self.source?.absoluteString

        if let navigation = self.navigationController {
            self.previousNavigationBarState = (navigation.navigationBar.tintColor, navigation.navigationBar.isHidden)
            self.previousToolbarState = (navigation.toolbar.tintColor, navigation.toolbar.isHidden)
        }

        //        self.restateViewHeight()

        if let s = self.source {
            self.load(source: s)
        } else {
            print("[\(type(of: self))][Error] Invalid url")
        }
    }

    open func setupViewElements() {
        self.setUpProgressView()
        self.setUpConstraints()
        self.addBarButtonItems()
    }

    @objc func restateViewHeight() {
        var bottomPadding = CGFloat(0.0)
        var topPadding = CGFloat(0.0)
        if #available(iOS 11.0, *) {
            let window = UIApplication.shared.keyWindow
            bottomPadding = window?.safeAreaInsets.bottom ?? 0.0
            topPadding = (window?.safeAreaInsets.top)!
        }
        if UIDevice.current.orientation.isPortrait {
            self.navigationController?.toolbar.isHidden = false
            if self.viewHeightPortrait == nil {
                self.viewHeightPortrait = self.view.safeAreaLayoutGuide.layoutFrame.size.height
                if toolbarItemTypes.count == 0 {
                    self.viewHeightPortrait! = self.viewHeightPortrait! + bottomPadding
                }
                if self.navigationController?.navigationBar.isHidden == true {
                    self.viewHeightPortrait = self.viewHeightPortrait! + topPadding
                }
            }
            self.currentViewHeight = self.viewHeightPortrait
        } else if UIDevice.current.orientation.isLandscape {
            self.navigationController?.toolbar.isHidden = false
            if self.viewHeightLandscape == nil {
                self.viewHeightLandscape = self.view.safeAreaLayoutGuide.layoutFrame.size.height
                if toolbarItemTypes.count == 0 {
                    self.viewHeightLandscape! = self.viewHeightLandscape! + bottomPadding
                }
                if self.navigationController?.navigationBar.isHidden == true {
                    self.viewHeightLandscape = self.viewHeightLandscape! + topPadding
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
        self.setupViewElements()
        setUpState()
    }

    override open func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        rollbackState()
    }

    override open func didReceiveMemoryWarning() {
        super.didReceiveMemoryWarning()
        // Dispose of any resources that can be recreated.
    }

    override open func observeValue(forKeyPath keyPath: String?, of object: Any?, change: [NSKeyValueChangeKey: Any]?, context: UnsafeMutableRawPointer?) {
        switch keyPath {
        case estimatedProgressKeyPath?:
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
        case titleKeyPath?:
            if self.hasDynamicTitle {
                self.navigationItem.title = webView?.url?.host
            }
        case "URL":
            self.capBrowserPlugin?.notifyListeners("urlChangeEvent", data: ["url": webView?.url?.absoluteString ?? ""])
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
        webView?.load(createRequest(url: remote))
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

        //        if presentingViewController != nil {
        switch doneBarButtonItemPosition {
        case .left:
            if !leftNavigaionBarItemTypes.contains(where: { type in
                switch type {
                case .done:
                    return true
                default:
                    return false
                }
            }) {
                leftNavigaionBarItemTypes.insert(.done, at: 0)
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
        //        }

        navigationItem.leftBarButtonItems = leftNavigaionBarItemTypes.map {
            barButtonItemType in
            if let barButtonItem = barButtonItem(barButtonItemType) {
                return barButtonItem
            }
            return UIBarButtonItem()
        }

        navigationItem.rightBarButtonItems = rightNavigaionBarItemTypes.map {
            barButtonItemType in
            if let barButtonItem = barButtonItem(barButtonItemType) {
                return barButtonItem
            }
            return UIBarButtonItem()
        }

        if toolbarItemTypes.count > 0 {
            for index in 0..<toolbarItemTypes.count - 1 {
                toolbarItemTypes.insert(.flexibleSpace, at: 2 * index + 1)
            }
        }

        setToolbarItems(toolbarItemTypes.map {
            barButtonItemType -> UIBarButtonItem in
            if let barButtonItem = barButtonItem(barButtonItemType) {
                return barButtonItem
            }
            return UIBarButtonItem()
        }, animated: true)
    }

    func updateBarButtonItems() {
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
        toolbarItems = toolbarItems?.map {
            barButtonItem -> UIBarButtonItem in
            return updateReloadBarButtonItem(barButtonItem, isLoading)
        }

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
        navigationController?.setToolbarHidden(toolbarItemTypes.count == 0, animated: true)

        if let tintColor = tintColor {
            progressView?.progressTintColor = tintColor
            navigationController?.navigationBar.tintColor = tintColor
            navigationController?.toolbar.tintColor = tintColor
        }
    }

    func rollbackState() {
        progressView?.progress = 0

        navigationController?.navigationBar.tintColor = previousNavigationBarState.tintColor
        navigationController?.toolbar.tintColor = previousToolbarState.tintColor

        navigationController?.setToolbarHidden(previousToolbarState.hidden, animated: true)
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
        let hosts = UrlsHandledByApp.hosts
        let schemes = UrlsHandledByApp.schemes
        let blank = UrlsHandledByApp.blank

        var tryToOpenURLWithApp = false
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
        webView?.goBack()
    }

    @objc func forwardDidClick(sender: AnyObject) {
        webView?.goForward()
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
        guard let s = self.source else {
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
        let showDisclaimer: Bool = self.shareDisclaimer != nil
        if showDisclaimer {
            let alert = UIAlertController(
                title: self.shareDisclaimer?["title"] as? String ?? "Title",
                message: self.shareDisclaimer?["message"] as? String ?? "Message",
                preferredStyle: UIAlertController.Style.alert)
            alert.addAction(UIAlertAction(title: self.shareDisclaimer?["confirmBtn"] as? String ?? "Confirm", style: UIAlertAction.Style.default, handler: { _ in
                self.shareDisclaimer = nil
                self.capBrowserPlugin?.notifyListeners("confirmBtnClicked", data: nil)
                let activityViewController = UIActivityViewController(activityItems: items, applicationActivities: nil)
                activityViewController.setValue(self.shareSubject ?? self.title, forKey: "subject")
                activityViewController.popoverPresentationController?.barButtonItem = (sender as! UIBarButtonItem)
                self.present(activityViewController, animated: true, completion: nil)
            }))
            alert.addAction(UIAlertAction(title: self.shareDisclaimer?["cancelBtn"] as? String ?? "Cancel", style: UIAlertAction.Style.default, handler: nil))
            self.present(alert, animated: true, completion: nil)
        } else {
            let activityViewController = UIActivityViewController(activityItems: items, applicationActivities: nil)
            activityViewController.setValue(self.shareSubject ?? self.title, forKey: "subject")
            activityViewController.popoverPresentationController?.barButtonItem = (sender as! UIBarButtonItem)
            self.present(activityViewController, animated: true, completion: nil)
        }
    }

    func closeView () {
        var canDismiss = true
        if let url = self.source?.url {
            canDismiss = delegate?.webViewController?(self, canDismiss: url) ?? true
        }
        if canDismiss {
            //            UIDevice.current.setValue(Int(UIInterfaceOrientation.portrait.rawValue), forKey: "orientation")
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
}

// MARK: - WKUIDelegate
extension WKWebViewController: WKUIDelegate {

}

// MARK: - WKNavigationDelegate
extension WKWebViewController: WKNavigationDelegate {
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
            self.capBrowserPlugin?.presentView()
        }
        didpageInit = true
        updateBarButtonItems()
        self.progressView?.progress = 0
        if let url = webView.url {
            self.url = url
            delegate?.webViewController?(self, didFinish: url)
        }
    }

    public func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        updateBarButtonItems()
        self.progressView?.progress = 0
        if let url = webView.url {
            self.url = url
            delegate?.webViewController?(self, didFail: url, withError: error)
        }
    }

    public func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        updateBarButtonItems()
        self.progressView?.progress = 0
        if let url = webView.url {
            self.url = url
            delegate?.webViewController?(self, didFail: url, withError: error)
        }
    }

    public func webView(_ webView: WKWebView, didReceive challenge: URLAuthenticationChallenge, completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        if let bypassedSSLHosts = bypassedSSLHosts, bypassedSSLHosts.contains(challenge.protectionSpace.host) {
            let credential = URLCredential(trust: challenge.protectionSpace.serverTrust!)
            completionHandler(.useCredential, credential)
        } else {
            completionHandler(.performDefaultHandling, nil)
        }
    }

    public func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        var actionPolicy: WKNavigationActionPolicy = .allow
        defer {
            decisionHandler(actionPolicy)
        }
        guard let u = navigationAction.request.url else {
            print("Cannot handle empty URLs")
            return
        }

        if !self.allowsFileURL && u.isFileURL {
            print("Cannot handle file URLs")
            return
        }

        if handleURLWithApp(u, targetFrame: navigationAction.targetFrame) {
            actionPolicy = .cancel
            return
        }

        if u.host == self.source?.url?.host, let cookies = availableCookies, !checkRequestCookies(navigationAction.request, cookies: cookies) {
            self.load(remote: u)
            actionPolicy = .cancel
            return
        }

        if let navigationType = NavigationType(rawValue: navigationAction.navigationType.rawValue), let result = delegate?.webViewController?(self, decidePolicy: u, navigationType: navigationType) {
            actionPolicy = result ? .allow : .cancel
        }
    }
}

class BlockBarButtonItem: UIBarButtonItem {

    var block: ((WKWebViewController) -> Void)?
}
