import Foundation

@objc public class InAppBrowser: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
