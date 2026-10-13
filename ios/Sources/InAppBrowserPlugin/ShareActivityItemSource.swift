import UIKit

final class ShareActivityItemSource: NSObject, UIActivityItemSource {
    private let item: Any
    private let subject: String?

    init(item: Any, subject: String?) {
        self.item = item
        self.subject = subject
    }

    func activityViewControllerPlaceholderItem(_ activityViewController: UIActivityViewController) -> Any {
        item
    }

    func activityViewController(
        _ activityViewController: UIActivityViewController,
        itemForActivityType activityType: UIActivity.ActivityType?
    ) -> Any? {
        item
    }

    func activityViewController(
        _ activityViewController: UIActivityViewController,
        subjectForActivityType activityType: UIActivity.ActivityType?
    ) -> String {
        subject ?? ""
    }
}
