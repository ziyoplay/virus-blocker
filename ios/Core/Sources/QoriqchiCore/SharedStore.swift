import Foundation

/// Ilova va kengaytmalar (Safari bloklovchi, SMS filtri) o'rtasidagi umumiy papka (App Group).
/// Sideloadly bilan o'rnatilganda App Group bo'lmasligi mumkin — unda [directory] nil bo'ladi va
/// kengaytmalar faqat ichki qoidalar (evristika) bilan ishlaydi.
public enum SharedStore {
    public static let groupID = "group.uz.apkqoriqchi.ios"

    public static var directory: URL? {
        #if canImport(Darwin)
        return FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupID)
        #else
        return nil
        #endif
    }

    public static var blockerListURL: URL? { directory?.appendingPathComponent("blockerList.json") }

    static func keysURL(_ feed: Blocklist.Feed) -> URL? { directory?.appendingPathComponent("\(feed.id).bin") }

    public static func saveKeys(_ keys: [UInt64], for feed: Blocklist.Feed) throws {
        guard let url = keysURL(feed) else { return }
        try Blocklist.encode(keys).write(to: url, options: .atomic)
    }

    /// Bazadagi barcha ro'yxatlar (yo'q yoki buzilganlari tashlab ketiladi).
    public static func loadSets() -> Blocklist.Sets {
        Blocklist.feeds.compactMap { feed in
            guard let url = keysURL(feed), let data = try? Data(contentsOf: url), let keys = Blocklist.decode(data) else { return nil }
            return (kind: feed.kind, keys: keys)
        }
    }

    /// Bazadagi domenlar soni va oxirgi yangilanish vaqti.
    public static func stats() -> (domains: Int, updated: Date?) {
        var count = 0
        var newest: Date?
        for feed in Blocklist.feeds {
            guard let url = keysURL(feed),
                  let attrs = try? FileManager.default.attributesOfItem(atPath: url.path),
                  let size = attrs[.size] as? NSNumber else { continue }
            count += max(0, (size.intValue - 8) / 8)
            if let date = attrs[.modificationDate] as? Date, newest.map({ date > $0 }) ?? true { newest = date }
        }
        return (count, newest)
    }
}
