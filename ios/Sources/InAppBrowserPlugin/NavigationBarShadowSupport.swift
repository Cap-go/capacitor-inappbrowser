import UIKit

enum NavigationBarShadowSupport {
    static func hideShadow(on navigationBar: UINavigationBar, backgroundColor: UIColor? = nil) {
        navigationBar.setBackgroundImage(UIImage(), for: .default)
        navigationBar.shadowImage = UIImage()

        if #available(iOS 13.0, *) {
            let appearance = UINavigationBarAppearance()
            if let backgroundColor {
                appearance.configureWithOpaqueBackground()
                appearance.backgroundColor = backgroundColor
            } else {
                appearance.configureWithDefaultBackground()
            }
            appearance.backgroundEffect = nil
            appearance.shadowColor = .clear
            appearance.shadowImage = UIImage()

            if let titleTextAttributes = navigationBar.titleTextAttributes {
                appearance.titleTextAttributes = titleTextAttributes
            }
            if let largeTitleTextAttributes = navigationBar.largeTitleTextAttributes {
                appearance.largeTitleTextAttributes = largeTitleTextAttributes
            }

            navigationBar.standardAppearance = appearance
            navigationBar.compactAppearance = appearance
            navigationBar.scrollEdgeAppearance = appearance
            navigationBar.compactScrollEdgeAppearance = appearance
        }
    }
}
