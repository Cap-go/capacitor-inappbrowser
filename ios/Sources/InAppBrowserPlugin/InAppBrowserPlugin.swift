import Foundation
import Capacitor
import WebKit
import AuthenticationServices

enum ActiveWebViewSupport {
    static func shouldActivateNewWebView(isHidden: Bool, hasActiveWebView: Bool) -> Bool {
        !isHidden || !hasActiveWebView
    }

    static func resolveVisibilityTarget(originatingWebViewId: String?, activeWebViewId: String?) -> String? {
        originatingWebViewId ?? activeWebViewId
    }
}

protocol ProxyRequestLocating {
    func hasPendingProxyRequest(_ requestId: String) -> Bool
}

enum ProxyResponseRoutingSupport {
    enum Resolution<T> {
        case matched(T)
        case ambiguous
        case missing
    }

    static func resolveTargetHandler<T: ProxyRequestLocating>(
        webviewId: String?,
        requestId: String,
        handlers: [String: T]
    ) -> Resolution<T> {
        if let webviewId, !webviewId.isEmpty {
            guard let handler = handlers[webviewId] else {
                return .missing
            }
            return .matched(handler)
        }

        var matchedHandler: T?
        for handler in handlers.values where handler.hasPendingProxyRequest(requestId) {
            if matchedHandler != nil {
                return .ambiguous
            }
            matchedHandler = handler
        }

        guard let matchedHandler else {
            return .missing
        }
        return .matched(matchedHandler)
    }
}

extension UIColor {

    convenience init(hexString: String) {
        let hex = hexString.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int = UInt64()
        Scanner(string: hex).scanHexInt64(&int)
        let components = (
            R: CGFloat((int >> 16) & 0xff) / 255,
            G: CGFloat((int >> 08) & 0xff) / 255,
            B: CGFloat((int >> 00) & 0xff) / 255
        )
        self.init(red: components.R, green: components.G, blue: components.B, alpha: 1)
    }

}

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(InAppBrowserPlugin)
public class InAppBrowserPlugin: CAPPlugin, CAPBridgedPlugin {
    enum InvisibilityMode: String {
        case aware = "AWARE"
        case fakeVisible = "FAKE_VISIBLE"
    }
    private let pluginVersion: String = "8.6.2"
    public let identifier = "InAppBrowserPlugin"
    public let jsName = "InAppBrowser"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "goBack", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "open", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openWebView", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clearCookies", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getCookies", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clearAllCookies", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clearCache", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "reload", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setUrl", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "show", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "hide", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "close", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "executeScript", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "postMessage", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "takeScreenshot", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "updateDimensions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setEnabledSafeTopMargin", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setEnabledSafeBottomMargin", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getPluginVersion", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openSecureWindow", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "handleProxyRequest", returnType: CAPPluginReturnPromise)
    ]
    var navigationWebViewController: UINavigationController?
    private var navigationControllers: [String: UINavigationController] = [:]
    private var privacyScreen: UIImageView?
    private var isSetupDone = false
    var currentPluginCall: CAPPluginCall?
    var isPresentAfterPageLoad = false
    var isHidden = false
    var invisibilityMode: InvisibilityMode = .aware
    var webViewController: WKWebViewController?
    private var webViewControllers: [String: WKWebViewController] = [:]
    private var proxySchemeHandlers: [String: ProxySchemeHandler] = [:]
    private var webViewStack: [String] = []
    private var activeWebViewId: String?
    private weak var presentationContainerView: UIView?
    private var presentationContainerWasInteractive = true
    private var presentationContainerPreviousAlpha: CGFloat = 1
    private var hiddenWebViewContainers: [ObjectIdentifier: UIView] = [:]
    private var closeModalTitle: String?
    private var closeModalDescription: String?
    private var closeModalOk: String?
    private var closeModalCancel: String?
    private var openSecureWindowCall: CAPPluginCall?

    private func setup() {
        self.isSetupDone = true

        #if swift(>=4.2)
        NotificationCenter.default.addObserver(self, selector: #selector(appDidBecomeActive(_:)), name: UIApplication.didBecomeActiveNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(appWillResignActive(_:)), name: UIApplication.willResignActiveNotification, object: nil)
        #else
        NotificationCenter.default.addObserver(self, selector: #selector(appDidBecomeActive(_:)), name: .UIApplicationDidBecomeActive, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(appWillResignActive(_:)), name: .UIApplicationWillResignActive, object: nil)
        #endif
    }

    private func setActiveWebView(id: String, webView: WKWebViewController, navigationController: UINavigationController) {
        activeWebViewId = id
        self.webViewController = webView
        self.navigationWebViewController = navigationController
    }

    private func registerWebView(
        id: String,
        webView: WKWebViewController,
        navigationController: UINavigationController,
        makeActive: Bool = true
    ) {
        webViewControllers[id] = webView
        navigationControllers[id] = navigationController
        webViewStack.removeAll { $0 == id }
        webViewStack.append(id)
        if makeActive || activeWebViewId == nil || self.webViewController == nil || self.navigationWebViewController == nil {
            setActiveWebView(id: id, webView: webView, navigationController: navigationController)
        }
    }

    private func unregisterWebView(id: String) {
        if let webView = webViewControllers[id]?.capableWebView {
            cleanupHiddenWebViewContainer(for: webView)
        }
        proxySchemeHandlers[id]?.cancelAllPendingTasks()
        proxySchemeHandlers[id] = nil
        webViewControllers[id] = nil
        navigationControllers[id] = nil
        webViewStack.removeAll { $0 == id }
        activeWebViewId = webViewStack.last
        if let activeId = activeWebViewId {
            self.webViewController = webViewControllers[activeId]
            self.navigationWebViewController = navigationControllers[activeId]
        } else {
            self.webViewController = nil
            self.navigationWebViewController = nil
        }
    }

    private func resolveNavigationController(for id: String?) -> UINavigationController? {
        if let id {
            return navigationControllers[id]
        }
        return navigationWebViewController
    }

    private func notifyPopupWindowOpened(id: String, parentId: String?, url: String?, visible: Bool) {
        var event: [String: Any] = [
            "id": id,
            "visible": visible
        ]
        if let parentId, !parentId.isEmpty {
            event["parentId"] = parentId
        }
        if let url, !url.isEmpty {
            event["url"] = url
        }
        notifyListeners("popupWindowOpened", data: event)
    }

    private func cloneNavigationAppearance(from source: UINavigationController?, to target: UINavigationController) {
        guard let source else { return }
        target.navigationBar.isTranslucent = source.navigationBar.isTranslucent
        target.toolbar.isTranslucent = source.toolbar.isTranslucent
        target.navigationBar.tintColor = source.navigationBar.tintColor
        target.navigationBar.barTintColor = source.navigationBar.barTintColor
        target.navigationBar.titleTextAttributes = source.navigationBar.titleTextAttributes
        target.toolbar.tintColor = source.toolbar.tintColor

        if #available(iOS 13.0, *) {
            target.navigationBar.standardAppearance = source.navigationBar.standardAppearance
            target.navigationBar.scrollEdgeAppearance = source.navigationBar.scrollEdgeAppearance
            target.navigationBar.compactAppearance = source.navigationBar.compactAppearance
            target.navigationBar.compactScrollEdgeAppearance = source.navigationBar.compactScrollEdgeAppearance
            target.toolbar.standardAppearance = source.toolbar.standardAppearance
            target.toolbar.compactAppearance = source.toolbar.compactAppearance
            target.toolbar.scrollEdgeAppearance = source.toolbar.scrollEdgeAppearance
        } else {
            target.navigationBar.setBackgroundImage(source.navigationBar.backgroundImage(for: .default), for: .default)
            target.navigationBar.shadowImage = source.navigationBar.shadowImage
        }
    }

    func createManagedPopupWebView(
        from parentController: WKWebViewController,
        configuration: WKWebViewConfiguration,
        navigationAction: WKNavigationAction
    ) -> WKWebView? {
        let popupId = UUID().uuidString
        let shouldHidePopup = parentController.hiddenPopupWindow
        if let parentWebView = parentController.capableWebView {
            configuration.processPool = parentWebView.configuration.processPool
        }
        if let dataStore = parentController.websiteDataStore() {
            configuration.websiteDataStore = dataStore
        }

        let proxyHandler = parentController.proxySchemeHandler?.duplicate(for: popupId)
        if let proxyHandler {
            proxySchemeHandlers[popupId] = proxyHandler
        }

        let popupController = WKWebViewController()
        guard let popupWebView = popupController.inheritPopupPresentation(
            from: parentController,
            request: navigationAction.request,
            configuration: configuration,
            instanceId: popupId,
            proxySchemeHandler: proxyHandler
        ) else {
            proxySchemeHandlers[popupId] = nil
            return nil
        }

        let navigationController = UINavigationController(rootViewController: popupController)
        cloneNavigationAppearance(from: parentController.navigationController, to: navigationController)
        let shouldActivatePopup = ActiveWebViewSupport.shouldActivateNewWebView(
            isHidden: shouldHidePopup,
            hasActiveWebView: activeWebViewId != nil
        )
        registerWebView(
            id: popupId,
            webView: popupController,
            navigationController: navigationController,
            makeActive: shouldActivatePopup
        )
        if shouldHidePopup {
            guard attachWebViewToWindow(popupWebView) else {
                unregisterWebView(id: popupId)
                return nil
            }
        } else {
            let presenter = self.bridge?.viewController?.presentedViewController ?? self.bridge?.viewController
            presenter?.present(navigationController, animated: true, completion: nil)
        }
        notifyPopupWindowOpened(
            id: popupId,
            parentId: parentController.instanceId,
            url: navigationAction.request.url?.absoluteString,
            visible: !shouldHidePopup
        )
        return popupWebView
    }

    private func resolveWebViewController(for id: String?) -> WKWebViewController? {
        if let id {
            return webViewControllers[id]
        }
        return webViewController
    }

    func cookieStore(for id: String) -> WKHTTPCookieStore? {
        resolveWebViewController(for: id)?.websiteDataStore()?.httpCookieStore
    }

    private func dataStores(for targetId: String?) -> [WKWebsiteDataStore] {
        if let targetId {
            guard let controller = webViewControllers[targetId],
                  let store = controller.websiteDataStore() else {
                return []
            }
            return [store]
        }

        let controllers = Array(webViewControllers.values)
        if controllers.isEmpty {
            return [WKWebsiteDataStore.default()]
        }

        var seen = Set<ObjectIdentifier>()
        var stores: [WKWebsiteDataStore] = []
        for controller in controllers {
            if let store = controller.websiteDataStore() {
                let identifier = ObjectIdentifier(store)
                if seen.insert(identifier).inserted {
                    stores.append(store)
                }
            }
        }
        return stores
    }

    private func parseProxyRules(_ rawRules: [Any]) throws -> [NativeProxyRule] {
        try rawRules.enumerated().map { index, item in
            guard let dictionary = item as? [String: Any] else {
                throw NSError(
                    domain: "InAppBrowserPlugin",
                    code: -1,
                    userInfo: [NSLocalizedDescriptionKey: "Proxy rule at index \(index) must be an object"]
                )
            }
            return try NativeProxyRule.from(dictionary: dictionary)
        }
    }

    func presentView(webViewId: String? = nil, isAnimated: Bool = true) {
        let resolvedId = webViewId ?? activeWebViewId
        let navigationController = resolvedId.flatMap { navigationControllers[$0] } ?? self.navigationWebViewController
        guard let navigationController else {
            self.currentPluginCall?.reject("Navigation controller is not initialized")
            return
        }

        let presenter = self.bridge?.viewController?.presentedViewController ?? self.bridge?.viewController
        presenter?.present(navigationController, animated: isAnimated, completion: {
            self.currentPluginCall?.resolve()
        })
    }

    private func activeWindow() -> UIWindow? {
        return UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive || $0.activationState == .foregroundInactive }?
            .windows
            .first { $0.isKeyWindow }
    }

    private func hiddenContainerFrame(in window: UIWindow) -> CGRect {
        switch self.invisibilityMode {
        case .aware:
            return CGRect(
                x: window.bounds.maxX + 2048,
                y: window.bounds.maxY + 2048,
                width: 1,
                height: 1
            )
        case .fakeVisible:
            return CGRect(
                x: window.bounds.maxX + 2048,
                y: 0,
                width: max(window.bounds.width, 1),
                height: max(window.bounds.height, 1)
            )
        }
    }

    private func ensureHiddenWebViewContainer(for webView: WKWebView, in window: UIWindow) -> UIView {
        let key = ObjectIdentifier(webView)
        let frame = hiddenContainerFrame(in: window)

        if let existingContainer = hiddenWebViewContainers[key] {
            existingContainer.frame = frame
            if existingContainer.superview !== window {
                existingContainer.removeFromSuperview()
                window.addSubview(existingContainer)
            }
            return existingContainer
        }

        let container = UIView(frame: frame)
        container.backgroundColor = .clear
        container.isOpaque = false
        container.isUserInteractionEnabled = false
        container.clipsToBounds = true
        container.accessibilityElementsHidden = true
        window.addSubview(container)
        hiddenWebViewContainers[key] = container
        return container
    }

    private func cleanupHiddenWebViewContainer(for webView: WKWebView?) {
        guard let webView else {
            return
        }

        let key = ObjectIdentifier(webView)
        if let container = hiddenWebViewContainers.removeValue(forKey: key) {
            container.removeFromSuperview()
        }
    }

    private func attachWebViewToWindow(_ webView: WKWebView) -> Bool {
        guard let window = activeWindow() else {
            return false
        }

        let container = ensureHiddenWebViewContainer(for: webView, in: window)
        webView.removeFromSuperview()
        webView.translatesAutoresizingMaskIntoConstraints = true
        webView.autoresizingMask = [.flexibleWidth, .flexibleHeight]

        switch self.invisibilityMode {
        case .aware:
            webView.frame = container.bounds
            webView.alpha = 1
            webView.isOpaque = false
            webView.backgroundColor = .clear
            webView.scrollView.backgroundColor = .clear
        case .fakeVisible:
            webView.frame = container.bounds
            webView.alpha = 1
            webView.isOpaque = false
            webView.backgroundColor = .clear
            webView.scrollView.backgroundColor = .clear
        }

        webView.isUserInteractionEnabled = false
        container.addSubview(webView)
        return true
    }

    private func attachWebViewToController(_ webViewController: WKWebViewController, webView: WKWebView) {
        cleanupHiddenWebViewContainer(for: webView)
        webView.removeFromSuperview()
        webView.translatesAutoresizingMaskIntoConstraints = false
        webViewController.view.addSubview(webView)

        let bottomAnchor = webViewController.enabledSafeBottomMargin
            ? webViewController.view.safeAreaLayoutGuide.bottomAnchor
            : webViewController.view.bottomAnchor

        let topAnchor = webViewController.enabledSafeTopMargin
            ? webViewController.view.safeAreaLayoutGuide.topAnchor
            : webViewController.view.topAnchor

        NSLayoutConstraint.activate([
            webView.topAnchor.constraint(equalTo: topAnchor),
            webView.leadingAnchor.constraint(equalTo: webViewController.view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: webViewController.view.trailingAnchor),
            webView.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])

        if let backgroundColor = webViewController.view.backgroundColor {
            webView.backgroundColor = backgroundColor
            webView.scrollView.backgroundColor = backgroundColor
        }
        webView.alpha = 1
        webView.isOpaque = true
        webView.isUserInteractionEnabled = true
    }

    func handleWebViewDidClose(id: String, url: String) {
        if !id.isEmpty, webViewControllers[id] != nil {
            self.notifyListeners("closeEvent", data: ["id": id, "url": url])
            unregisterWebView(id: id)
            return
        }

        cleanupHiddenWebViewContainer(for: self.webViewController?.capableWebView)
        self.notifyListeners("closeEvent", data: ["url": url])
        self.webViewController = nil
        self.navigationWebViewController = nil
    }

    @objc func clearAllCookies(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let targetId = call.getString("id")
            if let targetId, self.webViewControllers[targetId] == nil {
                call.reject("WebView is not initialized")
                return
            }

            let dataStores = self.dataStores(for: targetId)
            if dataStores.isEmpty {
                call.reject("WebView is not initialized")
                return
            }

            let dataTypes = Set([WKWebsiteDataTypeCookies])
            let group = DispatchGroup()
            for dataStore in dataStores {
                group.enter()
                dataStore.removeData(ofTypes: dataTypes,
                                     modifiedSince: Date(timeIntervalSince1970: 0)) {
                    group.leave()
                }
            }
            group.notify(queue: .main) {
                call.resolve()
            }
        }
    }

    @objc func clearCache(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let targetId = call.getString("id")
            if let targetId, self.webViewControllers[targetId] == nil {
                call.reject("WebView is not initialized")
                return
            }

            let dataStores = self.dataStores(for: targetId)
            if dataStores.isEmpty {
                call.reject("WebView is not initialized")
                return
            }

            let dataTypes = Set([WKWebsiteDataTypeDiskCache, WKWebsiteDataTypeMemoryCache])
            let group = DispatchGroup()
            for dataStore in dataStores {
                group.enter()
                dataStore.removeData(ofTypes: dataTypes,
                                     modifiedSince: Date(timeIntervalSince1970: 0)) {
                    group.leave()
                }
            }
            group.notify(queue: .main) {
                call.resolve()
            }
        }
    }

    @objc func clearCookies(_ call: CAPPluginCall) {
        guard let url = call.getString("url"),
              let host = URL(string: url)?.host else {
            call.reject("Invalid URL")
            return
        }

        DispatchQueue.main.async {
            let targetId = call.getString("id")
            if let targetId, self.webViewControllers[targetId] == nil {
                call.reject("WebView is not initialized")
                return
            }

            let dataStores = self.dataStores(for: targetId)
            if dataStores.isEmpty {
                call.reject("WebView is not initialized")
                return
            }

            let outerGroup = DispatchGroup()
            for dataStore in dataStores {
                outerGroup.enter()
                dataStore.httpCookieStore.getAllCookies { cookies in
                    let innerGroup = DispatchGroup()
                    for cookie in cookies {
                        if cookie.domain == host || cookie.domain.hasSuffix(".\(host)") || host.hasSuffix(cookie.domain) {
                            innerGroup.enter()
                            dataStore.httpCookieStore.delete(cookie) {
                                innerGroup.leave()
                            }
                        }
                    }
                    innerGroup.notify(queue: .main) {
                        outerGroup.leave()
                    }
                }
            }

            outerGroup.notify(queue: .main) {
                call.resolve()
            }
        }
    }

    @objc func getCookies(_ call: CAPPluginCall) {
        let urlString = call.getString("url") ?? ""
        let includeHttpOnly = call.getBool("includeHttpOnly") ?? true

        guard let url = URL(string: urlString), let host = url.host else {
            call.reject("Invalid URL")
            return
        }

        DispatchQueue.main.async {
            WKWebsiteDataStore.default().httpCookieStore.getAllCookies { cookies in
                var cookieDict = [String: String]()
                for cookie in cookies {

                    if (includeHttpOnly || !cookie.isHTTPOnly) && (cookie.domain == host || cookie.domain.hasSuffix(".\(host)") || host.hasSuffix(cookie.domain)) {
                        cookieDict[cookie.name] = cookie.value
                    }
                }
                call.resolve(cookieDict)
            }
        }

    }

    @objc func openWebView(_ call: CAPPluginCall) {
        if !self.isSetupDone {
            self.setup()
        }
        self.currentPluginCall = call

        guard let urlString = call.getString("url") else {
            call.reject("Must provide a URL to open")
            return
        }

        if urlString.isEmpty {
            call.reject("URL must not be empty")
            return
        }

        let webViewId = UUID().uuidString
        let showScreenshotButton = call.getBool("showScreenshotButton", false)
        let buttonNearDoneSettings = call.getObject("buttonNearDone")

        var buttonNearDoneIcon: UIImage?
        if let buttonNearDoneSettings {
            guard let iosSettingsRaw = buttonNearDoneSettings["ios"] else {
                call.reject("IOS settings not found")
                return
            }
            guard let iosSettings = iosSettingsRaw as? JSObject else {
                call.reject("IOS settings are not an object")
                return
            }

            guard let iconType = iosSettings["iconType"] as? String else {
                call.reject("buttonNearDone.iconType is empty")
                return
            }
            if iconType != "sf-symbol" && iconType != "asset" {
                call.reject("IconType is neither 'sf-symbol' nor 'asset'")
                return
            }
            guard let icon = iosSettings["icon"] as? String else {
                call.reject("buttonNearDone.icon is empty")
                return
            }

            if iconType == "sf-symbol" {
                buttonNearDoneIcon = UIImage(systemName: icon)?.withRenderingMode(.alwaysTemplate)
                print("[DEBUG] Set buttonNearDone SF Symbol icon: \(icon)")
            } else {
                // Look in app's web assets/public directory
                guard let webDir = Bundle.main.resourceURL?.appendingPathComponent("public") else {
                    call.reject("Failed to locate bundled web assets")
                    return
                }

                // Try several path combinations to find the asset
                let paths = [
                    icon,                    // Just the icon name
                    "public/\(icon)",        // With public/ prefix
                    icon.replacingOccurrences(of: "public/", with: "")  // Without public/ prefix
                ]

                var foundImage = false

                for path in paths {
                    // Try as a direct path from web assets dir
                    let assetPath = path.replacingOccurrences(of: "public/", with: "")
                    let fileURL = webDir.appendingPathComponent(assetPath)

                    print("[DEBUG] Trying to load from: \(fileURL.path)")

                    if FileManager.default.fileExists(atPath: fileURL.path),
                       let data = try? Data(contentsOf: fileURL),
                       let img = UIImage(data: data) {
                        buttonNearDoneIcon = img.withRenderingMode(.alwaysTemplate)
                        print("[DEBUG] Successfully loaded buttonNearDone from web assets: \(fileURL.path)")
                        foundImage = true
                        break
                    }

                    // Try with www directory as an alternative
                    if let wwwDir = Bundle.main.resourceURL?.appendingPathComponent("www") {
                        let wwwFileURL = wwwDir.appendingPathComponent(assetPath)

                        print("[DEBUG] Trying to load from www dir: \(wwwFileURL.path)")

                        if FileManager.default.fileExists(atPath: wwwFileURL.path),
                           let data = try? Data(contentsOf: wwwFileURL),
                           let img = UIImage(data: data) {
                            buttonNearDoneIcon = img.withRenderingMode(.alwaysTemplate)
                            print("[DEBUG] Successfully loaded buttonNearDone from www dir: \(wwwFileURL.path)")
                            foundImage = true
                            break
                        }
                    }

                    // Try looking in app bundle assets
                    if let iconImage = UIImage(named: path) {
                        buttonNearDoneIcon = iconImage.withRenderingMode(.alwaysTemplate)
                        print("[DEBUG] Successfully loaded buttonNearDone from app bundle: \(path)")
                        foundImage = true
                        break
                    }
                }

                if !foundImage {
                    call.reject("Failed to load buttonNearDone icon: \(icon)")
                    return
                }
            }
        }

        if showScreenshotButton, buttonNearDoneSettings != nil {
            call.reject("showScreenshotButton is not compatible with buttonNearDone")
            return
        }

        let headers = call.getObject("headers", [:]).mapValues { String(describing: $0 as Any) }
        let closeModal = call.getBool("closeModal", false)
        let closeModalTitle = call.getString("closeModalTitle", "Close")
        let closeModalDescription = call.getString("closeModalDescription", "Are you sure you want to close this window?")
        let closeModalOk = call.getString("closeModalOk", "OK")
        let closeModalCancel = call.getString("closeModalCancel", "Cancel")
        let closeModalURLPattern = call.getString("closeModalURLPattern")
        let isInspectable = call.getBool("isInspectable", false)
        let preventDeeplink = call.getBool("preventDeeplink", false)
        let openBlankTargetInWebView = call.getBool("openBlankTargetInWebView", false)
        let isAnimated = call.getBool("isAnimated", true)
        let enabledSafeBottomMargin = call.getBool("enabledSafeBottomMargin", false)
        let enabledSafeTopMargin = call.getBool("enabledSafeTopMargin", true)
        let hidden = call.getBool("hidden", false)
        self.isHidden = hidden
        let hiddenPopupWindow = call.getBool("hiddenPopupWindow", false)
        let allowWebViewJsVisibilityControl = self.getConfig().getBoolean("allowWebViewJsVisibilityControl", false)
        let allowScreenshotsFromWebPage = call.getBool("allowScreenshotsFromWebPage", false)
        let captureConsoleLogs = call.getBool("captureConsoleLogs", false)
        let handleDownloads = call.getBool("handleDownloads", false)
        let invisibilityModeRaw = call.getString("invisibilityMode", "AWARE")
        self.invisibilityMode = InvisibilityMode(rawValue: invisibilityModeRaw.uppercased()) ?? .aware

        // Validate preShowScript requires isPresentAfterPageLoad
        if call.getString("preShowScript") != nil && !call.getBool("isPresentAfterPageLoad", false) {
            call.reject("preShowScript requires isPresentAfterPageLoad to be true")
            return
        }

        // Validate closeModal options
        if closeModal {
            if call.getString("closeModalTitle") != nil ||
                call.getString("closeModalDescription") != nil ||
                call.getString("closeModalOk") != nil ||
                call.getString("closeModalCancel") != nil {
                // Store the values to be set after proper initialization
                self.closeModalTitle = closeModalTitle
                self.closeModalDescription = closeModalDescription
                self.closeModalOk = closeModalOk
                self.closeModalCancel = closeModalCancel
            }
            if let pattern = closeModalURLPattern {
                if (try? NSRegularExpression(pattern: pattern)) == nil {
                    call.reject("Invalid closeModalURLPattern regex")
                    return
                }
            }
        } else {
            // Reject if closeModal is false but closeModal options are provided
            if call.getString("closeModalTitle") != nil ||
                call.getString("closeModalDescription") != nil ||
                call.getString("closeModalOk") != nil ||
                call.getString("closeModalCancel") != nil ||
                call.getString("closeModalURLPattern") != nil {
                call.reject("closeModal options require closeModal to be true")
                return
            }
        }

        // Validate shareDisclaimer requires shareSubject
        if call.getString("shareSubject") == nil && call.getObject("shareDisclaimer") != nil {
            call.reject("shareDisclaimer requires shareSubject to be provided")
            return
        }

        // Validate buttonNearDone compatibility with toolbar type
        if buttonNearDoneSettings != nil || showScreenshotButton {
            let toolbarType = call.getString("toolbarType", "")
            if toolbarType == "activity" || toolbarType == "navigation" || toolbarType == "blank" {
                let optionName = showScreenshotButton ? "showScreenshotButton" : "buttonNearDone"
                call.reject(optionName + " is not compatible with toolbarType: " + toolbarType)
                return
            }
        }

        var disclaimerContent: JSObject?
        if let shareDisclaimerRaw = call.getObject("shareDisclaimer"), !shareDisclaimerRaw.isEmpty {
            disclaimerContent = shareDisclaimerRaw
        }

        let toolbarType = call.getString("toolbarType", "")
        let backgroundColor = call.getString("backgroundColor", "black") == "white" ? UIColor.white : UIColor.black

        // Don't null out shareDisclaimer regardless of toolbarType
        // if toolbarType != "activity" {
        //     disclaimerContent = nil
        // }

        let ignoreUntrustedSSLError = call.getBool("ignoreUntrustedSSLError", false)
        let enableGooglePaySupport = call.getBool("enableGooglePaySupport", false)
        let activeNativeNavigationForWebview = call.getBool("activeNativeNavigationForWebview", true)

        self.isPresentAfterPageLoad = call.getBool("isPresentAfterPageLoad", false)
        let showReloadButton = call.getBool("showReloadButton", false)

        let blockedHostsRaw = call.getArray("blockedHosts", [])
        let blockedHosts = blockedHostsRaw.compactMap { $0 as? String }

        let authorizedAppLinksRaw = call.getArray("authorizedAppLinks", [])
        let authorizedAppLinks = authorizedAppLinksRaw.compactMap { $0 as? String }

        let credentials = self.readCredentials(call)

        // Read HTTP method and body for custom requests
        let httpMethod = call.getString("method")
        let httpBody = call.getString("body")

        // Read dimension options
        let width = call.getFloat("width")
        let height = call.getFloat("height")
        let xPos = call.getFloat("x")
        let yPos = call.getFloat("y")

        // Read disableOverscroll option (iOS only - controls WebView bounce effect)
        let disableOverscroll = call.getBool("disableOverscroll", false)

        let legacyProxyRequests = ProxySchemeRequestSupport.legacyProxyRequestsConfiguration(from: call.options["proxyRequests"])
        let outboundProxyRulesRaw = call.getArray("outboundProxyRules", [])
        let inboundProxyRulesRaw = call.getArray("inboundProxyRules", [])
        let outboundProxyRules: [NativeProxyRule]
        let inboundProxyRules: [NativeProxyRule]
        do {
            outboundProxyRules = try parseProxyRules(outboundProxyRulesRaw)
            inboundProxyRules = try parseProxyRules(inboundProxyRulesRaw)
        } catch {
            call.reject("Invalid proxy rules: \(error.localizedDescription)")
            return
        }

        // Validate dimension parameters
        if width != nil && height == nil {
            call.reject("Height must be specified when width is provided")
            return
        }

        DispatchQueue.main.async {
            guard let url = URL(string: urlString) else {
                call.reject("Invalid URL format")
                return
            }

            var proxyHandler: ProxySchemeHandler?
            if legacyProxyRequests.isEnabled || !outboundProxyRules.isEmpty || !inboundProxyRules.isEmpty {
                proxyHandler = ProxySchemeHandler(
                    plugin: self,
                    webviewId: webViewId,
                    legacyProxyRequests: legacyProxyRequests.isEnabled,
                    legacyProxyRequestURLRegex: legacyProxyRequests.urlRegex,
                    outboundRules: outboundProxyRules,
                    inboundRules: inboundProxyRules
                )
                self.proxySchemeHandlers[webViewId] = proxyHandler
            }

            self.webViewController = WKWebViewController.init(
                url: url,
                headers: headers,
                isInspectable: isInspectable,
                credentials: credentials,
                preventDeeplink: preventDeeplink,
                blankNavigationTab: toolbarType == "blank",
                enabledSafeBottomMargin: enabledSafeBottomMargin,
                enabledSafeTopMargin: enabledSafeTopMargin,
                blockedHosts: blockedHosts,
                authorizedAppLinks: authorizedAppLinks,
                allowWebViewJsVisibilityControl: allowWebViewJsVisibilityControl,
                allowScreenshotsFromWebPage: allowScreenshotsFromWebPage,
                captureConsoleLogs: captureConsoleLogs,
                proxyRequests: legacyProxyRequests.isEnabled,
                proxySchemeHandler: proxyHandler,
                documentStartUserScripts: self.documentStartUserScripts(
                    authorizedAppLinks: authorizedAppLinks,
                    openBlankTargetInWebView: openBlankTargetInWebView
                ),
                openBlankTargetInWebView: openBlankTargetInWebView
            )

            guard let webViewController = self.webViewController else {
                call.reject("Failed to initialize WebViewController")
                return
            }

            webViewController.instanceId = webViewId

            // Set HTTP method and body if provided
            if let method = httpMethod {
                webViewController.httpMethod = method
            }
            if let body = httpBody {
                webViewController.httpBody = body
            }

            // Set dimensions if provided
            if let width = width {
                webViewController.customWidth = CGFloat(width)
            }
            if let height = height {
                webViewController.customHeight = CGFloat(height)
            }
            if let xPos = xPos {
                webViewController.customX = CGFloat(xPos)
            }
            if let yPos = yPos {
                webViewController.customY = CGFloat(yPos)
            }

            // Set disableOverscroll option
            webViewController.disableOverscroll = disableOverscroll
            webViewController.handleDownloads = handleDownloads

            // Set native navigation gestures before view loads
            webViewController.activeNativeNavigationForWebview = activeNativeNavigationForWebview

            // Update the webview's gesture property (if webview already exists)
            webViewController.updateNavigationGestures()

            if self.bridge?.statusBarVisible == true {
                let subviews = self.bridge?.webView?.superview?.subviews
                if let emptyStatusBarIndex = subviews?.firstIndex(where: { $0.subviews.isEmpty }) {
                    if let emptyStatusBar = subviews?[emptyStatusBarIndex] {
                        webViewController.capacitorStatusBar = emptyStatusBar
                        emptyStatusBar.removeFromSuperview()
                    }
                }
            }

            webViewController.source = .remote(url)
            webViewController.leftNavigationBarItemTypes = []

            // Configure close button based on showArrow
            let showArrow = call.getBool("showArrow", false)
            if showArrow {
                // When showArrow is true, put arrow on left
                webViewController.doneBarButtonItemPosition = .left
                webViewController.showArrowAsClose = true
            } else {
                // Default X on right
                webViewController.doneBarButtonItemPosition = toolbarType == "activity" ? .none : .right
                webViewController.showArrowAsClose = false
            }

            // Configure navigation buttons based on toolbarType
            if toolbarType == "activity" {
                // Activity mode should ONLY have:
                // 1. Close button (if not hidden by doneBarButtonItemPosition)
                // 2. Share button (if shareSubject is provided)
                webViewController.leftNavigationBarItemTypes = []  // Clear any left items
                webViewController.rightNavigaionBarItemTypes = []  // Clear any right items

                // Only add share button if subject is provided
                if call.getString("shareSubject") != nil {
                    // Add share button to right bar
                    webViewController.rightNavigaionBarItemTypes.append(.activity)
                    print("[DEBUG] Activity mode: Added share button, shareSubject: \(call.getString("shareSubject") ?? "nil")")
                } else {
                    // In activity mode, always make the share button visible by setting a default shareSubject
                    webViewController.shareSubject = "Share"
                    webViewController.rightNavigaionBarItemTypes.append(.activity)
                    print("[DEBUG] Activity mode: Setting default shareSubject")
                }

                // Set done button position based on showArrow
                if showArrow {
                    webViewController.doneBarButtonItemPosition = .left
                } else {
                    // In activity mode, keep the done button visible even when showArrow is false
                    webViewController.doneBarButtonItemPosition = .right
                }
            } else if toolbarType == "navigation" {
                // Navigation mode puts back/forward on the left
                webViewController.leftNavigationBarItemTypes = [.back, .forward]
                if showReloadButton {
                    webViewController.leftNavigationBarItemTypes.append(.reload)
                }

                // Only add share button if subject is provided
                if call.getString("shareSubject") != nil {
                    // Add share button to right navigation bar
                    webViewController.rightNavigaionBarItemTypes.append(.activity)
                }
            } else {
                // Other modes may have reload button
                if showReloadButton {
                    webViewController.leftNavigationBarItemTypes.append(.reload)
                }

                // Only add share button if subject is provided
                if call.getString("shareSubject") != nil {
                    // Add share button to right navigation bar
                    webViewController.rightNavigaionBarItemTypes.append(.activity)
                }
            }

            // Set buttonNearDoneIcon if provided
            if let buttonNearDoneIcon = buttonNearDoneIcon {
                webViewController.buttonNearDoneIcon = buttonNearDoneIcon
                print("[DEBUG] Button near done icon set: \(buttonNearDoneIcon)")
            } else if showScreenshotButton {
                webViewController.buttonNearDoneIcon = UIImage(systemName: "camera")?.withRenderingMode(.alwaysTemplate)
            }
            webViewController.showScreenshotButton = showScreenshotButton

            webViewController.capBrowserPlugin = self
            webViewController.title = call.getString("title", "New Window")
            // Only set shareSubject if not already set for activity mode
            if webViewController.shareSubject == nil {
                webViewController.shareSubject = call.getString("shareSubject")
            }
            webViewController.shareDisclaimer = disclaimerContent

            // Debug shareDisclaimer
            if let disclaimer = disclaimerContent {
                print("[DEBUG] Share disclaimer set: \(disclaimer)")
            } else {
                print("[DEBUG] No share disclaimer set")
            }

            webViewController.preShowScript = call.getString("preShowScript")
            webViewController.preShowScriptInjectionTime = call.getString("preShowScriptInjectionTime", "pageLoad")

            // If script should be injected at document start, inject it now
            if webViewController.preShowScriptInjectionTime == "documentStart" {
                webViewController.injectPreShowScriptAtDocumentStart()
            }

            webViewController.websiteTitleInNavigationBar = call.getBool("visibleTitle", true)
            webViewController.ignoreUntrustedSSLError = ignoreUntrustedSSLError

            // Set Google Pay support
            webViewController.enableGooglePaySupport = enableGooglePaySupport
            webViewController.hiddenPopupWindow = hiddenPopupWindow
            webViewController.opensHidden = hidden
            webViewController.captureConsoleLogs = captureConsoleLogs

            // Set text zoom if specified
            if let textZoom = call.getInt("textZoom") {
                webViewController.textZoom = textZoom
            }

            // Set closeModal properties after proper initialization
            if closeModal {
                webViewController.closeModal = true
                webViewController.closeModalTitle = self.closeModalTitle ?? closeModalTitle
                webViewController.closeModalDescription = self.closeModalDescription ?? closeModalDescription
                webViewController.closeModalOk = self.closeModalOk ?? closeModalOk
                webViewController.closeModalCancel = self.closeModalCancel ?? closeModalCancel
                if let pattern = closeModalURLPattern {
                    webViewController.closeModalURLPattern = pattern
                }
            }

            self.navigationWebViewController = UINavigationController.init(rootViewController: webViewController)
            if let navigationController = self.navigationWebViewController {
                self.registerWebView(id: webViewId, webView: webViewController, navigationController: navigationController)
            }
            self.navigationWebViewController?.navigationBar.isTranslucent = false
            self.navigationWebViewController?.toolbar.isTranslucent = false

            // Ensure no lines or borders appear by default
            self.navigationWebViewController?.navigationBar.setBackgroundImage(UIImage(), for: .default)
            self.navigationWebViewController?.navigationBar.shadowImage = UIImage()
            self.navigationWebViewController?.navigationBar.setValue(true, forKey: "hidesShadow")
            self.navigationWebViewController?.toolbar.setShadowImage(UIImage(), forToolbarPosition: .any)

            // Handle web view background color
            webViewController.view.backgroundColor = backgroundColor

            // Handle toolbar color
            if let toolbarColor = call.getString("toolbarColor"), self.isHexColorCode(toolbarColor) {
                // If specific color provided, use it
                let color = UIColor(hexString: toolbarColor)

                // Apply to status bar and navigation bar area with a single colored view
                webViewController.setupStatusBarBackground(color: color)

                // Set status bar style based on toolbar color
                let isDark = self.isDarkColor(color)
                webViewController.statusBarStyle = isDark ? .lightContent : .darkContent
                webViewController.updateStatusBarStyle()

                // Apply text color
                let textColor: UIColor
                if let toolbarTextColor = call.getString("toolbarTextColor"), self.isHexColorCode(toolbarTextColor) {
                    textColor = UIColor(hexString: toolbarTextColor)
                } else {
                    textColor = isDark ? UIColor.white : UIColor.black
                }

                // Apply tint color to all UI elements without changing background
                self.navigationWebViewController?.navigationBar.tintColor = textColor
                webViewController.tintColor = textColor
                self.navigationWebViewController?.navigationBar.titleTextAttributes = [NSAttributedString.Key.foregroundColor: textColor]
            } else {
                // Use system appearance
                let isDarkMode = UITraitCollection.current.userInterfaceStyle == .dark
                let backgroundColor = isDarkMode ? UIColor.black : UIColor.white
                let textColor: UIColor

                if let toolbarTextColor = call.getString("toolbarTextColor"), self.isHexColorCode(toolbarTextColor) {
                    textColor = UIColor(hexString: toolbarTextColor)
                } else {
                    textColor = isDarkMode ? UIColor.white : UIColor.black
                }

                // Apply colors
                webViewController.setupStatusBarBackground(color: backgroundColor)
                webViewController.tintColor = textColor
                self.navigationWebViewController?.navigationBar.tintColor = textColor
                self.navigationWebViewController?.navigationBar.titleTextAttributes = [NSAttributedString.Key.foregroundColor: textColor]
                webViewController.statusBarStyle = isDarkMode ? .lightContent : .darkContent
                webViewController.updateStatusBarStyle()

            }

            // Configure modal presentation for touch passthrough if custom dimensions are set
            if width != nil || height != nil {
                self.navigationWebViewController?.modalPresentationStyle = .overFullScreen

                // Create a pass-through container
                let containerView = PassThroughView()
                containerView.backgroundColor = .clear

                // Calculate dimensions - use screen width if only height is provided
                let finalWidth = width.map { CGFloat($0) } ?? UIScreen.main.bounds.width
                let finalHeight = height.map { CGFloat($0) } ?? UIScreen.main.bounds.height

                containerView.targetFrame = CGRect(
                    x: CGFloat(xPos ?? 0),
                    y: CGFloat(yPos ?? 0),
                    width: finalWidth,
                    height: finalHeight
                )

                // Replace the navigation controller's view with our pass-through container
                if let navController = self.navigationWebViewController,
                   let originalView = navController.view {
                    navController.view = containerView
                    containerView.addSubview(originalView)
                    originalView.frame = CGRect(
                        x: CGFloat(xPos ?? 0),
                        y: CGFloat(yPos ?? 0),
                        width: finalWidth,
                        height: finalHeight
                    )
                }
            } else {
                self.navigationWebViewController?.modalPresentationStyle = .overCurrentContext
            }

            self.navigationWebViewController?.modalTransitionStyle = .crossDissolve
            if toolbarType == "blank" {
                self.navigationWebViewController?.navigationBar.isHidden = true
                webViewController.blankNavigationTab = true

                // Even with hidden navigation bar, we need to set proper status bar appearance
                // If toolbarColor is explicitly set, use that for status bar style
                if let toolbarColor = call.getString("toolbarColor"), self.isHexColorCode(toolbarColor) {
                    let color = UIColor(hexString: toolbarColor)
                    let isDark = self.isDarkColor(color)
                    webViewController.statusBarStyle = isDark ? .lightContent : .darkContent
                    webViewController.updateStatusBarStyle()

                    // Apply status bar background color via the special view
                    webViewController.setupStatusBarBackground(color: color)

                    // Apply background color to whole view to ensure no gaps
                    webViewController.view.backgroundColor = color
                    self.navigationWebViewController?.view.backgroundColor = color

                    // Apply status bar background color
                    if let navController = self.navigationWebViewController {
                        navController.view.backgroundColor = color
                    }
                } else {
                    // Follow system appearance if no specific color
                    let isDarkMode = UITraitCollection.current.userInterfaceStyle == .dark
                    let backgroundColor = isDarkMode ? UIColor.black : UIColor.white
                    webViewController.statusBarStyle = isDarkMode ? .lightContent : .darkContent
                    webViewController.updateStatusBarStyle()

                    // Apply status bar background color via the special view
                    webViewController.setupStatusBarBackground(color: backgroundColor)

                    // Set appropriate background color
                    if let navController = self.navigationWebViewController {
                        navController.view.backgroundColor = backgroundColor
                    }
                }

            }

            // We don't use the toolbar anymore, always hide it
            self.navigationWebViewController?.setToolbarHidden(true, animated: false)

            if hidden {
                guard let webView = webViewController.capableWebView else {
                    call.reject("Failed to get webview for hidden mode")
                    return
                }
                // Zero-frame in window hierarchy required for WKWebView JS execution when hidden
                if !self.attachWebViewToWindow(webView) {
                    call.reject("Failed to get active window for hidden webview")
                    return
                }
            } else if !self.isPresentAfterPageLoad {
                self.presentView(webViewId: webViewId, isAnimated: isAnimated)
            }
            call.resolve(["id": webViewId])
        }
    }

    @objc func goBack(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let targetId = call.getString("id") ?? self.activeWebViewId
            guard let webViewController = self.resolveWebViewController(for: targetId) else {
                call.resolve(["canGoBack": false])
                return
            }

            let canGoBack = webViewController.goBack()
            call.resolve(["canGoBack": canGoBack])
        }
    }

    @objc func reload(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let targetId = call.getString("id") ?? self.activeWebViewId
            guard let webViewController = self.resolveWebViewController(for: targetId) else {
                call.reject("WebView is not initialized")
                return
            }

            webViewController.reload()
            call.resolve()
        }
    }

    @objc func setUrl(_ call: CAPPluginCall) {
        guard let urlString = call.getString("url") else {
            call.reject("Cannot get new url to set")
            return
        }

        guard let url = URL(string: urlString) else {
            call.reject("Invalid URL")
            return
        }

        let targetId = call.getString("id")
        guard let webViewController = self.resolveWebViewController(for: targetId) else {
            call.reject("WebView is not initialized")
            return
        }

        webViewController.load(remote: url)
        call.resolve()
    }

    private func setHiddenState(_ hidden: Bool, targetId: String?, call: CAPPluginCall?) {
        DispatchQueue.main.async {
            let resolvedId = targetId ?? self.activeWebViewId
            guard let webViewController = self.resolveWebViewController(for: resolvedId),
                  let webView = webViewController.capableWebView else {
                call?.reject("WebView is not initialized")
                return
            }
            let navigationController = self.resolveNavigationController(for: resolvedId)

            self.isHidden = hidden

            if hidden {
                if let navController = navigationController, navController.presentingViewController != nil {
                    navController.view.isHidden = true
                    navController.view.isUserInteractionEnabled = false
                    if let containerView = navController.view.superview {
                        if self.presentationContainerView == nil || self.presentationContainerView !== containerView {
                            self.presentationContainerView = containerView
                            self.presentationContainerWasInteractive = containerView.isUserInteractionEnabled
                            self.presentationContainerPreviousAlpha = containerView.alpha
                        }
                        containerView.isUserInteractionEnabled = false
                        containerView.alpha = 0
                    }
                }

                if !self.attachWebViewToWindow(webView) {
                    call?.reject("Failed to get active window for hidden webview")
                    return
                }
            } else {
                if webView.superview !== webViewController.view {
                    self.attachWebViewToController(webViewController, webView: webView)
                }

                if let navController = navigationController {
                    if let resolvedId {
                        self.setActiveWebView(id: resolvedId, webView: webViewController, navigationController: navController)
                    }
                    navController.view.isHidden = false
                    navController.view.isUserInteractionEnabled = true
                    if let containerView = self.presentationContainerView {
                        containerView.isUserInteractionEnabled = self.presentationContainerWasInteractive
                        containerView.alpha = self.presentationContainerPreviousAlpha
                        self.presentationContainerView = nil
                        self.presentationContainerWasInteractive = true
                        self.presentationContainerPreviousAlpha = 1
                    }

                    if navController.presentingViewController == nil {
                        let presenter = self.bridge?.viewController?.presentedViewController ?? self.bridge?.viewController
                        presenter?.present(navController, animated: true, completion: {
                            call?.resolve()
                        })
                        return
                    }
                }
            }

            call?.resolve()
        }
    }

    func setHiddenFromJavaScript(_ hidden: Bool, sourceId: String? = nil) {
        self.setHiddenState(
            hidden,
            targetId: ActiveWebViewSupport.resolveVisibilityTarget(
                originatingWebViewId: sourceId,
                activeWebViewId: activeWebViewId
            ),
            call: nil
        )
    }

    @objc func hide(_ call: CAPPluginCall) {
        self.setHiddenState(true, targetId: call.getString("id"), call: call)
    }

    @objc func show(_ call: CAPPluginCall) {
        self.setHiddenState(false, targetId: call.getString("id"), call: call)
    }

    @objc func executeScript(_ call: CAPPluginCall) {
        guard let script = call.getString("code") else {
            call.reject("Cannot get script to execute")
            return
        }
        DispatchQueue.main.async {
            if let targetId = call.getString("id") {
                guard let webViewController = self.webViewControllers[targetId] else {
                    call.reject("WebView is not initialized")
                    return
                }
                webViewController.executeScript(script: script)
                call.resolve()
                return
            }

            if !self.webViewControllers.isEmpty {
                self.webViewControllers.values.forEach { $0.executeScript(script: script) }
                call.resolve()
                return
            }

            guard let webViewController = self.webViewController else {
                call.reject("WebView is not initialized")
                return
            }

            webViewController.executeScript(script: script)
            call.resolve()
        }
    }

    @objc func postMessage(_ call: CAPPluginCall) {
        let eventData = call.getObject("detail", [:])
        // Check if eventData is empty
        if eventData.isEmpty {
            call.reject("Event data must not be empty")
            return
        }
        print("Event data: \(eventData)")

        DispatchQueue.main.async {
            if let targetId = call.getString("id") {
                guard let webViewController = self.webViewControllers[targetId] else {
                    call.reject("WebView is not initialized")
                    return
                }
                webViewController.postMessageToJS(message: eventData)
                call.resolve()
                return
            }

            if !self.webViewControllers.isEmpty {
                self.webViewControllers.values.forEach { $0.postMessageToJS(message: eventData) }
                call.resolve()
                return
            }

            guard let webViewController = self.webViewController else {
                call.reject("WebView is not initialized")
                return
            }

            webViewController.postMessageToJS(message: eventData)
            call.resolve()
        }
    }

    @objc func takeScreenshot(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let targetId = call.getString("id") ?? self.activeWebViewId
            guard let webViewController = self.resolveWebViewController(for: targetId) else {
                call.reject("WebView is not initialized")
                return
            }

            webViewController.takeScreenshot { result in
                switch result {
                case .success(let screenshot):
                    call.resolve(screenshot)
                case .failure(let error):
                    call.reject(error.localizedDescription)
                }
            }
        }
    }

    private func normalizedAuthorizedHosts(from links: [String]) -> [String] {
        links.compactMap { link in
            guard let host = URLComponents(string: link)?.host?.lowercased() else {
                return nil
            }

            return host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
        }
    }

    private func blankTargetInWebViewScript(authorizedAppLinks: [String]) -> String {
        let authorizedHosts = normalizedAuthorizedHosts(from: authorizedAppLinks)
        let hostsData = try? JSONSerialization.data(withJSONObject: authorizedHosts)
        let hostsJSON = hostsData.flatMap { String(data: $0, encoding: .utf8) } ?? "[]"

        return """
        (function() {
          const authorizedHosts = new Set(\(hostsJSON));
          const normalizeHost = (host) => (host || '').replace(/^www\\./i, '').toLowerCase();

          document.addEventListener('click', function(event) {
            const element = event.target;
            if (!element || typeof element.closest !== 'function') {
              return;
            }

            const anchor = element.closest('a[target="_blank"][href]');
            if (!anchor) {
              return;
            }

            let nextUrl;
            try {
              nextUrl = new URL(anchor.getAttribute('href'), window.location.href);
            } catch (_) {
              return;
            }

            const protocol = nextUrl.protocol.toLowerCase();
            if (protocol !== 'http:' && protocol !== 'https:') {
              return;
            }

            if (authorizedHosts.has(normalizeHost(nextUrl.hostname))) {
              return;
            }

            event.preventDefault();
            setTimeout(function() {
              window.location.assign(nextUrl.toString());
            }, 0);
          }, true);
        })();
        """
    }

    private func documentStartUserScripts(
        authorizedAppLinks: [String],
        openBlankTargetInWebView: Bool
    ) -> [String] {
        guard openBlankTargetInWebView else {
            return []
        }

        return [blankTargetInWebViewScript(authorizedAppLinks: authorizedAppLinks)]
    }

    func isHexColorCode(_ input: String) -> Bool {
        let hexColorRegex = "^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$"

        do {
            let regex = try NSRegularExpression(pattern: hexColorRegex)
            let range = NSRange(location: 0, length: input.utf16.count)
            if regex.firstMatch(in: input, options: [], range: range) != nil {
                return true
            }
        } catch {
            print("Error creating regular expression: \(error)")
        }

        return false
    }

    @objc func open(_ call: CAPPluginCall) {
        if !self.isSetupDone {
            self.setup()
        }

        let isInspectable = call.getBool("isInspectable", false)
        let preventDeeplink = call.getBool("preventDeeplink", false)
        self.isPresentAfterPageLoad = call.getBool("isPresentAfterPageLoad", false)

        self.currentPluginCall = call

        guard let urlString = call.getString("url") else {
            call.reject("Must provide a URL to open")
            return
        }

        if urlString.isEmpty {
            call.reject("URL must not be empty")
            return
        }

        let headers = call.getObject("headers", [:]).mapValues { String(describing: $0 as Any) }
        let credentials = self.readCredentials(call)

        DispatchQueue.main.async {
            guard let url = URL(string: urlString) else {
                call.reject("Invalid URL format")
                return
            }

            self.webViewController = WKWebViewController.init(url: url, headers: headers, isInspectable: isInspectable, credentials: credentials, preventDeeplink: preventDeeplink, blankNavigationTab: true, enabledSafeBottomMargin: false, enabledSafeTopMargin: true)

            guard let webViewController = self.webViewController else {
                call.reject("Failed to initialize WebViewController")
                return
            }

            if self.bridge?.statusBarVisible == true {
                let subviews = self.bridge?.webView?.superview?.subviews
                if let emptyStatusBarIndex = subviews?.firstIndex(where: { $0.subviews.isEmpty }) {
                    if let emptyStatusBar = subviews?[emptyStatusBarIndex] {
                        webViewController.capacitorStatusBar = emptyStatusBar
                        emptyStatusBar.removeFromSuperview()
                    }
                }
            }

            webViewController.source = .remote(url)
            webViewController.leftNavigationBarItemTypes = [.back, .forward, .reload]
            webViewController.capBrowserPlugin = self
            webViewController.hasDynamicTitle = true

            self.navigationWebViewController = UINavigationController.init(rootViewController: webViewController)
            self.navigationWebViewController?.navigationBar.isTranslucent = false

            // Ensure no lines or borders appear by default
            self.navigationWebViewController?.navigationBar.setBackgroundImage(UIImage(), for: .default)
            self.navigationWebViewController?.navigationBar.shadowImage = UIImage()
            self.navigationWebViewController?.navigationBar.setValue(true, forKey: "hidesShadow")

            // Use system appearance
            let isDarkMode = UITraitCollection.current.userInterfaceStyle == .dark
            let backgroundColor = isDarkMode ? UIColor.black : UIColor.white
            let textColor = isDarkMode ? UIColor.white : UIColor.black

            // Apply colors
            webViewController.setupStatusBarBackground(color: backgroundColor)
            webViewController.tintColor = textColor
            self.navigationWebViewController?.navigationBar.tintColor = textColor
            self.navigationWebViewController?.navigationBar.titleTextAttributes = [NSAttributedString.Key.foregroundColor: textColor]
            webViewController.statusBarStyle = isDarkMode ? .lightContent : .darkContent
            webViewController.updateStatusBarStyle()

            // Always hide toolbar to ensure no bottom bar
            self.navigationWebViewController?.setToolbarHidden(true, animated: false)

            self.navigationWebViewController?.modalPresentationStyle = .overCurrentContext
            self.navigationWebViewController?.modalTransitionStyle = .crossDissolve

            if !self.isPresentAfterPageLoad {
                self.presentView()
            }
            call.resolve()
        }
    }

    @objc func handleProxyRequest(_ call: CAPPluginCall) {
        guard let requestId = call.getString("requestId") else {
            call.reject("requestId is required")
            return
        }

        let webviewId = call.getString("webviewId")
        let phase = call.getString("phase")
        let decisionObj = call.getObject("decision") ?? call.getObject("response")

        let handler: ProxySchemeHandler?
        switch ProxyResponseRoutingSupport.resolveTargetHandler(
            webviewId: webviewId,
            requestId: requestId,
            handlers: proxySchemeHandlers
        ) {
        case .matched(let proxyHandler):
            handler = proxyHandler
        case .ambiguous:
            call.reject("webviewId is required when multiple webviews are open")
            return
        case .missing:
            handler = nil
        }

        guard let proxyHandler = handler else {
            if let webviewId,
               webViewControllers[webviewId] == nil,
               navigationControllers[webviewId] == nil {
                print("[InAppBrowser][Proxy] Ignoring late proxy response for closed webview \(webviewId)")
                call.resolve()
                return
            }
            call.reject("No proxy handler found")
            return
        }

        var responseDict: [String: Any]?
        if let decisionObj = decisionObj {
            var dict: [String: Any] = [:]
            if let cancel = decisionObj["cancel"] as? Bool {
                dict["cancel"] = cancel
            }
            if let request = decisionObj["request"] as? JSObject {
                var requestDict: [String: Any] = [:]
                requestDict["url"] = request["url"]
                requestDict["method"] = request["method"]
                requestDict["body"] = request["body"]
                if let headers = request["headers"] as? JSObject {
                    var headersDict: [String: String] = [:]
                    for (key, value) in headers {
                        if let strValue = value as? String {
                            headersDict[key] = strValue
                        }
                    }
                    requestDict["headers"] = headersDict
                }
                dict["request"] = requestDict
            }

            let responsePayload = (decisionObj["response"] as? JSObject) ?? decisionObj
            if responsePayload["status"] != nil {
                var responsePayloadDict: [String: Any] = [:]
                responsePayloadDict["status"] = responsePayload["status"]
                responsePayloadDict["body"] = responsePayload["body"]
                if let headers = responsePayload["headers"] as? JSObject {
                    var headersDict: [String: String] = [:]
                    for (key, value) in headers {
                        if let strValue = value as? String {
                            headersDict[key] = strValue
                        }
                    }
                    responsePayloadDict["headers"] = headersDict
                }
                if decisionObj["response"] != nil {
                    dict["response"] = responsePayloadDict
                } else {
                    dict.merge(responsePayloadDict) { _, new in new }
                }
            }
            responseDict = dict
        }

        proxyHandler.handleResponse(requestId: requestId, phase: phase, responseData: responseDict)
        call.resolve()
    }

    @objc func close(_ call: CAPPluginCall) {
        let isAnimated = call.getBool("isAnimated", true)

        DispatchQueue.main.async {
            let targetId = call.getString("id") ?? self.activeWebViewId
            if let targetId,
               let webViewController = self.webViewControllers[targetId],
               let navigationController = self.navigationControllers[targetId] {
                let currentUrl = webViewController.url?.absoluteString ?? ""
                webViewController.cleanupWebView()
                self.handleWebViewDidClose(id: targetId, url: currentUrl)
                navigationController.dismiss(animated: isAnimated, completion: nil)
                call.resolve()
                return
            }

            guard let webViewController = self.webViewController,
                  let navigationController = self.navigationWebViewController else {
                call.reject("WebView is not initialized")
                return
            }

            let currentUrl = webViewController.url?.absoluteString ?? ""
            let isPresented = navigationController.presentingViewController != nil

            if self.isHidden {
                webViewController.capableWebView?.removeFromSuperview()
                webViewController.cleanupWebView()
                if isPresented {
                    navigationController.dismiss(animated: isAnimated) {
                        self.handleWebViewDidClose(id: "", url: currentUrl)
                    }
                } else {
                    self.handleWebViewDidClose(id: "", url: currentUrl)
                }
                self.isHidden = false
                call.resolve()
                return
            }

            webViewController.cleanupWebView()
            self.handleWebViewDidClose(id: "", url: currentUrl)
            navigationController.dismiss(animated: isAnimated, completion: nil)
            call.resolve()
        }
    }

    private func showPrivacyScreen() {
        if privacyScreen == nil {
            let newPrivacyScreen = UIImageView()
            self.privacyScreen = newPrivacyScreen
            if let launchImage = UIImage(named: "LaunchImage") {
                newPrivacyScreen.image = launchImage
                newPrivacyScreen.frame = UIScreen.main.bounds
                newPrivacyScreen.contentMode = .scaleAspectFill
                newPrivacyScreen.isUserInteractionEnabled = false
            } else if let launchImage = UIImage(named: "Splash") {
                newPrivacyScreen.image = launchImage
                newPrivacyScreen.frame = UIScreen.main.bounds
                newPrivacyScreen.contentMode = .scaleAspectFill
                newPrivacyScreen.isUserInteractionEnabled = false
            }
        }
        if let screen = self.privacyScreen {
            self.navigationWebViewController?.view.addSubview(screen)
        }
    }

    private func hidePrivacyScreen() {
        self.privacyScreen?.removeFromSuperview()
    }

    @objc func appDidBecomeActive(_ notification: NSNotification) {
        self.hidePrivacyScreen()
    }

    @objc func appWillResignActive(_ notification: NSNotification) {
        self.showPrivacyScreen()
    }

    private func readCredentials(_ call: CAPPluginCall) -> WKWebViewCredentials? {
        var credentials: WKWebViewCredentials?
        let credentialsDict = call.getObject("credentials", [:]).mapValues { String(describing: $0 as Any) }
        if !credentialsDict.isEmpty, let username = credentialsDict["username"], let password = credentialsDict["password"] {
            credentials = WKWebViewCredentials(username: username, password: password)
        }
        return credentials
    }

    private func isDarkColor(_ color: UIColor) -> Bool {
        let components = color.cgColor.components ?? []
        let red = components[0]
        let green = components[1]
        let blue = components[2]
        let brightness = (red * 299 + green * 587 + blue * 114) / 1000
        return brightness < 0.5
    }

    @objc func getPluginVersion(_ call: CAPPluginCall) {
        call.resolve(["version": self.pluginVersion])
    }

    @objc func updateDimensions(_ call: CAPPluginCall) {
        let width = call.getFloat("width")
        let height = call.getFloat("height")
        let xPos = call.getFloat("x")
        let yPos = call.getFloat("y")

        DispatchQueue.main.async {
            let targetId = call.getString("id") ?? self.activeWebViewId
            guard let webViewController = self.resolveWebViewController(for: targetId) else {
                call.reject("WebView is not initialized")
                return
            }

            webViewController.updateDimensions(
                width: width.map { CGFloat($0) },
                height: height.map { CGFloat($0) },
                xPos: xPos.map { CGFloat($0) },
                yPos: yPos.map { CGFloat($0) }
            )

            call.resolve()
        }
    }

    @objc func setEnabledSafeTopMargin(_ call: CAPPluginCall) {
        if call.options["enabled"] == nil {
            print("[InAppBrowser][Warning] setEnabledSafeTopMargin called without 'enabled'; defaulting to true")
        }
        let enabled = call.getBool("enabled", true)
        DispatchQueue.main.async {
            let targetId = call.getString("id") ?? self.activeWebViewId
            guard let webViewController = self.resolveWebViewController(for: targetId) else {
                call.reject("WebView is not initialized")
                return
            }
            webViewController.updateSafeTopMargin(enabled)
            call.resolve()
        }
    }

    @objc func setEnabledSafeBottomMargin(_ call: CAPPluginCall) {
        if call.options["enabled"] == nil {
            print("[InAppBrowser][Warning] setEnabledSafeBottomMargin called without 'enabled'; defaulting to false")
        }
        let enabled = call.getBool("enabled", false)
        DispatchQueue.main.async {
            let targetId = call.getString("id") ?? self.activeWebViewId
            guard let webViewController = self.resolveWebViewController(for: targetId) else {
                call.reject("WebView is not initialized")
                return
            }
            webViewController.updateSafeBottomMargin(enabled)
            call.resolve()
        }
    }

    @objc func openSecureWindow(_ call: CAPPluginCall) {
        guard let urlString = call.getString("authEndpoint") else {
            call.reject("authEndpoint is required")
            return
        }

        guard let url = URL(string: urlString) else {
            call.reject("Invalid URL")
            return
        }

        guard let redirectUri = call.getString("redirectUri") else {
            call.reject("Redirect URI is required")
            return
        }

        // Store the call for later resolution
        self.openSecureWindowCall = call

        let prefersEphemeral = call.getBool("prefersEphemeralWebBrowserSession") ?? false

        // Open the URL in a secure browser window
        DispatchQueue.main.async {
            let session = ASWebAuthenticationSession(url: url, callbackURLScheme: url.scheme) {
                callbackURL, error in

                // Clean up the stored call
                self.openSecureWindowCall = nil

                if let error = error {
                    // Handle error (e.g., user cancelled)
                    call.reject(error.localizedDescription)
                    return
                }

                guard let callbackURL = callbackURL else {
                    call.reject("No callback URL received")
                    return
                }

                if !callbackURL.absoluteString.hasPrefix(redirectUri) {
                    call.reject("Redirect URI does not match, expected " + redirectUri + " but got " + callbackURL.absoluteString)
                    return
                }

                // Resolve the call with the callback URL
                call.resolve(["redirectedUri": callbackURL.absoluteString])
            }

            // Present the session
            session.prefersEphemeralWebBrowserSession = prefersEphemeral
            session.presentationContextProvider = self
            session.start()
        }
    }
}

extension InAppBrowserPlugin: ASWebAuthenticationPresentationContextProviding {
    public func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        return self.bridge?.viewController?.view.window ?? ASPresentationAnchor()
    }
}
