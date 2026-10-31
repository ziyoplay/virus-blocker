import Foundation
import QoriqchiCore

/// Safari bloklovchi qoidalari, tartib bo'yicha: ilova yangilagani (umumiy papka — faqat pullik akkauntda),
/// ilova yig'ilayotganda ichiga joylangani (bepul Apple ID'da asosiy yo'l), oxirida — faqat ichki qoidalar.
final class ContentBlockerRequestHandler: NSObject, NSExtensionRequestHandling {
    func beginRequest(with context: NSExtensionContext) {
        let item = NSExtensionItem()
        if let url = rulesURL(), let provider = NSItemProvider(contentsOf: url) {
            item.attachments = [provider]
        }
        context.completeRequest(returningItems: [item], completionHandler: nil)
    }

    private func rulesURL() -> URL? {
        if let shared = SharedStore.blockerListURL, FileManager.default.fileExists(atPath: shared.path) {
            return shared
        }
        if let bundled = SharedStore.bundledBlockerListURL {
            return bundled
        }
        let fallback = FileManager.default.temporaryDirectory.appendingPathComponent("blockerList.json")
        do {
            try BlockerRules.json(domains: []).write(to: fallback, options: .atomic)
            return fallback
        } catch {
            return nil
        }
    }
}
