import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Xavfli saytlar bazasi: ochiq ro'yxatlar kuniga bir marta yuklab olinadi. Har bir domen 64-bitli
/// xesh sifatida saralangan massivda turadi, tekshiruv — ikkilik qidiruv. Android'dagi `WebDb` bilan
/// bir xil manbalar va bir xil xesh (FNV-1a 64).
public enum Blocklist {
    public struct Feed {
        public let id: String
        public let url: URL
        public let kind: ListKind
    }

    public static let feeds = [
        // URLhaus (abuse.ch) — hozir virus tarqatayotgan saytlar
        Feed(id: "urlhaus", url: URL(string: "https://malware-filter.gitlab.io/malware-filter/urlhaus-filter-domains-online.txt")!, kind: .malware),
        // PhishTank / OpenPhish / PhishStats asosidagi phishing ro'yxati
        Feed(id: "phishing-filter", url: URL(string: "https://malware-filter.gitlab.io/malware-filter/phishing-filter-domains.txt")!, kind: .phishing),
        Feed(id: "phishing-army", url: URL(string: "https://phishing.army/download/phishing_army_blocklist.txt")!, kind: .phishing),
    ]

    public typealias Sets = [(kind: ListKind, keys: [UInt64])]

    static let magic: UInt32 = 0x5157_4231 // "QWB1"
    static let minEntries = 10

    /// FNV-1a 64 (UTF-16 bo'yicha — Android'dagi Kotlin `Char` bilan bir xil).
    public static func hash(_ s: String) -> UInt64 {
        var h: UInt64 = 0xcbf2_9ce4_8422_2325
        for c in s.utf16 {
            h ^= UInt64(c)
            h = h &* 0x0000_0100_0000_01b3
        }
        return h
    }

    /// "domen", "0.0.0.0 domen", "||domen^" ko'rinishidagi qatorlar.
    public static func parseLine<S: StringProtocol>(_ line: S) -> String? {
        let s = line.trimmingCharacters(in: .whitespaces)
        guard let first = s.first, first != "#", first != "!" else { return nil }
        let parts = s.split(whereSeparator: { $0 == " " || $0 == "\t" })
        var d = String(parts.count > 1 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1") ? parts[1] : parts[0]).lowercased()
        if d.hasPrefix("||") { d.removeFirst(2) }
        if d.hasSuffix("^") { d.removeLast() }
        if d.hasPrefix("www.") { d.removeFirst(4) }
        guard isDomain(d) else { return nil }
        return d
    }

    static func isDomain(_ d: String) -> Bool {
        guard let dot = d.lastIndex(of: "."), dot != d.startIndex, d.index(after: dot) != d.endIndex else { return false }
        return d.utf8.allSatisfy { c in
            (c >= 0x61 && c <= 0x7a) || (c >= 0x30 && c <= 0x39) || c == 0x2e || c == 0x2d
        }
    }

    /// Ro'yxat matnidan domenlar (takrorsiz, fayldagi tartibda).
    public static func domains(in text: String) -> [String] {
        var seen = Set<String>()
        var out: [String] = []
        for line in text.split(whereSeparator: \.isNewline) {
            if let d = parseLine(line), seen.insert(d).inserted { out.append(d) }
        }
        return out
    }

    public static func keys(_ domains: [String]) -> [UInt64] {
        Array(Set(domains.map(hash))).sorted()
    }

    /// Sayt yoki uning yuqori domeni bazada bo'lsa — qaysi ro'yxatda ekani.
    public static func lookup(_ sets: Sets, host: String) -> ListKind? {
        var h = Substring(host)
        while true {
            let key = hash(String(h))
            if let hit = sets.first(where: { contains($0.keys, key) }) { return hit.kind }
            guard let dot = h.firstIndex(of: "."), h[h.index(after: dot)...].contains(".") else { return nil }
            h = h[h.index(after: dot)...]
        }
    }

    static func contains(_ keys: [UInt64], _ key: UInt64) -> Bool {
        var lo = 0
        var hi = keys.count - 1
        while lo <= hi {
            let mid = (lo + hi) / 2
            if keys[mid] == key { return true }
            if keys[mid] < key { lo = mid + 1 } else { hi = mid - 1 }
        }
        return false
    }

    // MARK: - Fayl formati (Android bilan bir xil emas — bu yerda little-endian, faqat iOS ichida o'qiladi)

    public static func encode(_ keys: [UInt64]) -> Data {
        var data = Data(capacity: 8 + keys.count * 8)
        withUnsafeBytes(of: magic.littleEndian) { data.append(contentsOf: $0) }
        withUnsafeBytes(of: UInt32(keys.count).littleEndian) { data.append(contentsOf: $0) }
        for k in keys { withUnsafeBytes(of: k.littleEndian) { data.append(contentsOf: $0) } }
        return data
    }

    public static func decode(_ data: Data) -> [UInt64]? {
        guard data.count >= 8 else { return nil }
        func u32(_ at: Int) -> UInt32 {
            data.subdata(in: at ..< at + 4).withUnsafeBytes { UInt32(littleEndian: $0.loadUnaligned(as: UInt32.self)) }
        }
        guard u32(0) == magic else { return nil }
        let n = Int(u32(4))
        guard data.count == 8 + n * 8 else { return nil }
        return data.subdata(in: 8 ..< data.count).withUnsafeBytes { raw in
            (0 ..< n).map { UInt64(littleEndian: raw.loadUnaligned(fromByteOffset: $0 * 8, as: UInt64.self)) }
        }
    }

    // MARK: - Yuklab olish

    public enum DownloadError: Error {
        case http(Int)
        case empty
    }

    /// Ro'yxatni yuklab, domenlarini qaytaradi. Wi-Fi kirish sahifasi HTML qaytarsa — xato.
    public static func download(_ feed: Feed, session: URLSession = .shared) async throws -> [String] {
        var request = URLRequest(url: feed.url, timeoutInterval: 60)
        request.setValue("Qoriqchi-iOS", forHTTPHeaderField: "User-Agent")
        let (data, response): (Data, URLResponse) = try await withCheckedThrowingContinuation { cont in
            session.dataTask(with: request) { data, response, error in
                if let data = data, let response = response {
                    cont.resume(returning: (data, response))
                } else {
                    cont.resume(throwing: error ?? URLError(.unknown))
                }
            }.resume()
        }
        if let http = response as? HTTPURLResponse, http.statusCode != 200 { throw DownloadError.http(http.statusCode) }
        let list = domains(in: String(decoding: data, as: UTF8.self))
        if list.count < minEntries { throw DownloadError.empty }
        return list
    }
}
