import Foundation

/// Bazani qayerdan o'qish/qayerga yozish.
///
/// Bepul Apple ID'da App Group yaratib bo'lmaydi (Apple "not available" deydi), shuning uchun har bir
/// qism (ilova, Safari bloklovchi, SMS filtri) o'z papkasini ishlatadi. Kengaytmalar esa ilova yig'ilayotganda
/// ichiga joylangan bazani ([Bundle.main]) oladi — u har kuni CI'da yangilanadi.
public enum SharedStore {
    public static let groupID = "group.uz.apkqoriqchi.ios"

    /// Umumiy papka (faqat pullik akkauntda, App Group yoqilgan bo'lsa).
    public static var groupDirectory: URL? {
        #if canImport(Darwin)
        return FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupID)
        #else
        return nil
        #endif
    }

    /// Yangilangan baza yoziladigan papka: umumiy papka yoki shu qismning o'z papkasi.
    public static var directory: URL? {
        if let group = groupDirectory { return group }
        guard let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else { return nil }
        let dir = support.appendingPathComponent("Qoriqchi", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    /// Safari bloklovchi uchun yangilangan qoidalar — faqat umumiy papka bo'lsa ma'noli.
    public static var blockerListURL: URL? { groupDirectory?.appendingPathComponent("blockerList.json") }

    /// Ilova yig'ilayotganda joylangan qoidalar.
    public static var bundledBlockerListURL: URL? { Bundle.main.url(forResource: "blockerList", withExtension: "json") }

    static func keysURL(_ feed: Blocklist.Feed) -> URL? { directory?.appendingPathComponent("\(feed.id).bin") }

    static func bundledKeysURL(_ feed: Blocklist.Feed) -> URL? { Bundle.main.url(forResource: feed.id, withExtension: "bin") }

    public static func saveKeys(_ keys: [UInt64], for feed: Blocklist.Feed) throws {
        guard let url = keysURL(feed) else { return }
        try Blocklist.encode(keys).write(to: url, options: .atomic)
    }

    /// Har bir ro'yxat: yangilangani bo'lsa — u, bo'lmasa ichiga joylangani.
    public static func loadSets() -> Blocklist.Sets {
        Blocklist.feeds.compactMap { feed in
            for url in [keysURL(feed), bundledKeysURL(feed)].compactMap({ $0 }) {
                if let data = try? Data(contentsOf: url), let keys = Blocklist.decode(data) {
                    return (kind: feed.kind, keys: keys)
                }
            }
            return nil
        }
    }

    /// Bazadagi domenlar soni va oxirgi yangilanish vaqti.
    public static func stats() -> (domains: Int, updated: Date?) {
        var count = 0
        var newest: Date?
        for feed in Blocklist.feeds {
            guard let url = [keysURL(feed), bundledKeysURL(feed)].compactMap({ $0 }).first(where: { FileManager.default.fileExists(atPath: $0.path) }),
                  let attrs = try? FileManager.default.attributesOfItem(atPath: url.path),
                  let size = attrs[.size] as? NSNumber else { continue }
            count += max(0, (size.intValue - 8) / 8)
            if let date = attrs[.modificationDate] as? Date, newest.map({ date > $0 }) ?? true { newest = date }
        }
        return (count, newest)
    }
}
