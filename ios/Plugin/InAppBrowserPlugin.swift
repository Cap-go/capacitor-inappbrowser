import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(InAppBrowserPlugin)
public class InAppBrowserPlugin: CAPPlugin {
    var navigationWebViewController: UINavigationController?
    private var privacyScreen: UIImageView?
    private var isSetupDone = false
    var currentPluginCall: CAPPluginCall?
    var isPresentAfterPageLoad = false
    var webViewController: WKWebViewController?

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

    func presentView() {
        self.bridge?.viewController?.present(self.navigationWebViewController!, animated: true, completion: {
            self.currentPluginCall?.resolve()
        })
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

        let headers = call.getObject("headers", [:]).mapValues { String(describing: $0 as Any) }

        var disclaimerContent = call.getObject("shareDisclaimer")
        let toolbarType = call.getString("toolbarType", "")
        let backgroundColor = call.getString("backgroundColor", "black") == "white" ? UIColor.white : UIColor.black
        if toolbarType != "activity" {
            disclaimerContent = nil
        }

        self.isPresentAfterPageLoad = call.getBool("isPresentAfterPageLoad", false)

        DispatchQueue.main.async {
            let url = URL(string: urlString)

            if self.isPresentAfterPageLoad {
                self.webViewController = WKWebViewController.init(url: url!, headers: headers)
            } else {
                self.webViewController = WKWebViewController.init()
                self.webViewController?.setHeaders(headers: headers)
            }

            self.webViewController?.source = .remote(url!)
            self.webViewController?.leftNavigaionBarItemTypes = self.getToolbarItems(toolbarType: toolbarType)
            self.webViewController?.toolbarItemTypes = []
            self.webViewController?.doneBarButtonItemPosition = .right
            self.webViewController?.capBrowserPlugin = self
            self.webViewController?.title = call.getString("title", "")
            self.webViewController?.shareSubject = call.getString("shareSubject")
            self.webViewController?.shareDisclaimer = disclaimerContent
            self.navigationWebViewController = UINavigationController.init(rootViewController: self.webViewController!)
            self.navigationWebViewController?.navigationBar.isTranslucent = false
            self.navigationWebViewController?.toolbar.isTranslucent = false
            self.navigationWebViewController?.navigationBar.backgroundColor = backgroundColor
            self.navigationWebViewController?.toolbar.backgroundColor = backgroundColor
            self.navigationWebViewController?.modalPresentationStyle = .fullScreen
            if toolbarType == "blank" {
                self.navigationWebViewController?.navigationBar.isHidden = true
            }
            if !self.isPresentAfterPageLoad {
                self.presentView()
            }
        }
    }

    func getToolbarItems(toolbarType: String) -> [BarButtonItemType] {
        var result: [BarButtonItemType] = []
        if toolbarType == "activity" {
            result.append(.activity)
        } else if toolbarType == "navigation" {
            result.append(.back)
            result.append(.forward)
        }
        return result
    }

    @objc func setUrl(_ call: CAPPluginCall) {
        guard let url = call.getString("url") else {
            call.reject("Cannot get new url to set")
            return
        }
        self.webViewController?.load(remote: URL(string: url)!)
        call.resolve()
    }

    @objc func open(_ call: CAPPluginCall) {
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

        let headers = call.getObject("headers", [:]).mapValues { String(describing: $0 as Any) }

        self.isPresentAfterPageLoad = call.getBool("isPresentAfterPageLoad", false)

        DispatchQueue.main.async {
            let url = URL(string: urlString)

            if self.isPresentAfterPageLoad {
                self.webViewController = WKWebViewController.init(url: url!, headers: headers)
            } else {
                self.webViewController = WKWebViewController.init()
                self.webViewController?.setHeaders(headers: headers)
            }

            self.webViewController?.source = .remote(url!)
            self.webViewController?.leftNavigaionBarItemTypes = [.reload]
            self.webViewController?.toolbarItemTypes = [.back, .forward, .activity]
            self.webViewController?.capBrowserPlugin = self
            self.webViewController?.hasDynamicTitle = true
            self.navigationWebViewController = UINavigationController.init(rootViewController: self.webViewController!)
            self.navigationWebViewController?.navigationBar.isTranslucent = false
            self.navigationWebViewController?.toolbar.isTranslucent = false
            self.navigationWebViewController?.navigationBar.backgroundColor = .white
            self.navigationWebViewController?.toolbar.backgroundColor = .white
            self.navigationWebViewController?.modalPresentationStyle = .fullScreen
            if !self.isPresentAfterPageLoad {
                self.presentView()
            }
        }
    }

    @objc func close(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            self.navigationWebViewController?.dismiss(animated: true, completion: nil)
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
}
