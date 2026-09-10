import Foundation

/// Maps the `closeButtonPosition` option onto the navigation bar side of the done button.
enum CloseButtonPositionSupport {
    static func navigationBarPosition(for closeButtonPosition: String?) -> NavigationBarPosition? {
        switch closeButtonPosition {
        case "start":
            return .left
        case "end":
            return .right
        default:
            return nil
        }
    }
}
