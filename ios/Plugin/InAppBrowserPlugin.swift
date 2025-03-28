import Foundation
import Capacitor
import WebKit

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
    public let identifier = "InAppBrowserPlugin"
    public let jsName = "InAppBrowser"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "open", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openWebView", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clearCookies", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getCookies", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clearAllCookies", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clearCache", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "reload", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setUrl", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "show", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "close", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "executeScript", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "postMessage", returnType: CAPPluginReturnPromise)
    ]
    var navigationWebViewController: UINavigationController?
    private var privacyScreen: UIImageView?
    private var isSetupDone = false
    var currentPluginCall: CAPPluginCall?
    var isPresentAfterPageLoad = false
    var webViewController: WKWebViewController?
    private var closeModalTitle: String?
    private var closeModalDescription: String?
    private var closeModalOk: String?
    private var closeModalCancel: String?

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

    func presentView(isAnimated: Bool = true) {
        guard let navigationController = self.navigationWebViewController else {
            self.currentPluginCall?.reject("Navigation controller is not initialized")
            return
        }

        self.bridge?.viewController?.present(navigationController, animated: isAnimated, completion: {
            self.currentPluginCall?.resolve()
        })
    }

    @objc func clearAllCookies(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let dataStore = WKWebsiteDataStore.default()
            let dataTypes = Set([WKWebsiteDataTypeCookies])

            dataStore.removeData(ofTypes: dataTypes,
                                 modifiedSince: Date(timeIntervalSince1970: 0)) {
                call.resolve()
            }
        }
    }

    @objc func clearCache(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            let dataStore = WKWebsiteDataStore.default()
            let dataTypes = Set([WKWebsiteDataTypeDiskCache, WKWebsiteDataTypeMemoryCache])

            dataStore.removeData(ofTypes: dataTypes,
                                 modifiedSince: Date(timeIntervalSince1970: 0)) {
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
            WKWebsiteDataStore.default().httpCookieStore.getAllCookies { cookies in
                let group = DispatchGroup()
                for cookie in cookies {
                    if cookie.domain == host || cookie.domain.hasSuffix(".\(host)") || host.hasSuffix(cookie.domain) {
                        group.enter()
                        WKWebsiteDataStore.default().httpCookieStore.delete(cookie) {
                            group.leave()
                        }
                    }
                }

                group.notify(queue: .main) {
                    call.resolve()
                }
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

        var buttonNearDoneIcon: UIImage?
        if let buttonNearDoneSettings = call.getObject("buttonNearDone") {
            guard let iosSettingsRaw = buttonNearDoneSettings["ios"] else {
                call.reject("IOS settings not found")
                return
            }
            if !(iosSettingsRaw is JSObject) {
                call.reject("IOS settings are not an object")
                return
            }
            let iosSettings = iosSettingsRaw as! JSObject

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
                    print("[DEBUG] Failed to locate web assets directory")
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
                    print("[DEBUG] Failed to load buttonNearDone icon: \(icon)")

                    // Debug info
                    if let resourceURL = Bundle.main.resourceURL {
                        print("[DEBUG] Resource URL: \(resourceURL.path)")

                        // List directories to help debugging
                        do {
                            let contents = try FileManager.default.contentsOfDirectory(atPath: resourceURL.path)
                            print("[DEBUG] Root bundle contents: \(contents)")

                            // Check if public or www directories exist
                            if contents.contains("public") {
                                let publicContents = try FileManager.default.contentsOfDirectory(
                                    atPath: resourceURL.appendingPathComponent("public").path)
                                print("[DEBUG] Public dir contents: \(publicContents)")
                            }

                            if contents.contains("www") {
                                let wwwContents = try FileManager.default.contentsOfDirectory(
                                    atPath: resourceURL.appendingPathComponent("www").path)
                                print("[DEBUG] WWW dir contents: \(wwwContents)")
                            }
                        } catch {
                            print("[DEBUG] Error listing directories: \(error)")
                        }
                    }
                }
            }
        }

        let headers = call.getObject("headers", [:]).mapValues { String(describing: $0 as Any) }
        let closeModal = call.getBool("closeModal", false)
        let closeModalTitle = call.getString("closeModalTitle", "Close")
        let closeModalDescription = call.getString("closeModalDescription", "Are you sure you want to close this window?")
        let closeModalOk = call.getString("closeModalOk", "OK")
        let closeModalCancel = call.getString("closeModalCancel", "Cancel")
        let isInspectable = call.getBool("isInspectable", false)
        let preventDeeplink = call.getBool("preventDeeplink", false)
        let isAnimated = call.getBool("isAnimated", true)

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
        } else {
            // Reject if closeModal is false but closeModal options are provided
            if call.getString("closeModalTitle") != nil ||
                call.getString("closeModalDescription") != nil ||
                call.getString("closeModalOk") != nil ||
                call.getString("closeModalCancel") != nil {
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
        if call.getString("buttonNearDone") != nil {
            let toolbarType = call.getString("toolbarType", "")
            if toolbarType == "activity" || toolbarType == "navigation" || toolbarType == "blank" {
                call.reject("buttonNearDone is not compatible with toolbarType: " + toolbarType)
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

        self.isPresentAfterPageLoad = call.getBool("isPresentAfterPageLoad", false)
        let showReloadButton = call.getBool("showReloadButton", false)

        let credentials = self.readCredentials(call)

        DispatchQueue.main.async {
            guard let url = URL(string: urlString) else {
                call.reject("Invalid URL format")
                return
            }

            self.webViewController = WKWebViewController.init(url: url, headers: headers, isInspectable: isInspectable, credentials: credentials, preventDeeplink: preventDeeplink, blankNavigationTab: toolbarType == "blank")

            guard let webViewController = self.webViewController else {
                call.reject("Failed to initialize WebViewController")
                return
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
            }

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
            webViewController.websiteTitleInNavigationBar = call.getBool("visibleTitle", true)
            webViewController.ignoreUntrustedSSLError = ignoreUntrustedSSLError

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
            }

            self.navigationWebViewController = UINavigationController.init(rootViewController: webViewController)
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

            self.navigationWebViewController?.modalPresentationStyle = .fullScreen
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

            if !self.isPresentAfterPageLoad {
                self.presentView(isAnimated: isAnimated)
            }
            call.resolve()
        }
    }

    @objc func reload(_ call: CAPPluginCall) {
        self.webViewController?.reload()
        call.resolve()
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

        self.webViewController?.load(remote: url)
        call.resolve()
    }

    @objc func executeScript(_ call: CAPPluginCall) {
        guard let script = call.getString("code") else {
            call.reject("Cannot get script to execute")
            return
        }
        DispatchQueue.main.async {
            self.webViewController?.executeScript(script: script)
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
            self.webViewController?.postMessageToJS(message: eventData)
        }
        call.resolve()
    }

    func isHexColorCode(_ input: String) -> Bool {
        let hexColorRegex = "^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$"

        do {
            let regex = try NSRegularExpression(pattern: hexColorRegex)
            let range = NSRange(location: 0, length: input.utf16.count)
            if let _ = regex.firstMatch(in: input, options: [], range: range) {
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

            self.webViewController = WKWebViewController.init(url: url, headers: headers, isInspectable: isInspectable, credentials: credentials, preventDeeplink: preventDeeplink, blankNavigationTab: true)

            guard let webViewController = self.webViewController else {
                call.reject("Failed to initialize WebViewController")
                return
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

            self.navigationWebViewController?.modalPresentationStyle = .fullScreen

            if !self.isPresentAfterPageLoad {
                self.presentView()
            }
            call.resolve()
        }
    }

    @objc func close(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            self.navigationWebViewController?.dismiss(animated: true, completion: nil)
            self.notifyListeners("closeEvent", data: ["url": self.webViewController?.url?.absoluteString ?? ""])
            call.resolve()
        }
    }

    private func showPrivacyScreen() {
        if privacyScreen == nil {
            self.privacyScreen = UIImageView()
            if let launchImage = UIImage(named: "LaunchImage") {
                privacyScreen!.image = launchImage
                privacyScreen!.frame = UIScreen.main.bounds
                privacyScreen!.contentMode = .scaleAspectFill
                privacyScreen!.isUserInteractionEnabled = false
            } else if let launchImage = UIImage(named: "Splash") {
                privacyScreen!.image = launchImage
                privacyScreen!.frame = UIScreen.main.bounds
                privacyScreen!.contentMode = .scaleAspectFill
                privacyScreen!.isUserInteractionEnabled = false
            }
        }
        self.navigationWebViewController?.view.addSubview(self.privacyScreen!)
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
}
