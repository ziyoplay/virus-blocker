import Foundation
import QoriqchiCore

/// Safari bloklovchi: ilova yuklagan qoidalarni (umumiy papkadan) beradi. Ular hali yo'q bo'lsa yoki
/// App Group ishlamasa — ichki qoidalar (ma'lum serverlar, soxta Payme/Click/bank saytlari).
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
        let fallback = FileManager.default.temporaryDirectory.appendingPathComponent("blockerList.json")
        do {
            try BlockerRules.json(domains: []).write(to: fallback, options: .atomic)
            return fallback
        } catch {
            return nil
        }
    }
}
