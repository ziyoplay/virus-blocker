import Foundation

/// SMS ichidagi havolalarni tekshiradi: "Kartangiz bloklandi: payme-bonus.top/login" kabi xabarlar
/// SMS filtri orqali "Junk" bo'limiga tushadi.
public enum SmsRules {
    static let link = try! NSRegularExpression(
        pattern: #"(https?://[^\s]+|www\.[^\s]+|[\p{L}0-9-]+(\.[\p{L}0-9-]+)*\.\p{L}{2,}(/[^\s]*)?)"#
    )

    /// Matndagi havolaga o'xshash bo'laklar.
    public static func links(in text: String) -> [String] {
        let lower = text.lowercased()
        return link.matches(in: lower, range: NSRange(lower.startIndex..., in: lower)).compactMap { m in
            Range(m.range, in: lower).map { String(lower[$0]).trimmingCharacters(in: CharacterSet(charactersIn: ".,!?;:)\"'»")) }
        }
    }

    /// Eng xavfli havolaning hukmi; xavfli havola bo'lmasa — nil.
    public static func verdict(for text: String, lookup: (String) -> ListKind?) -> SiteVerdict? {
        links(in: text).compactMap { WebRules.check($0, lookup: lookup) }.max { $0.score < $1.score }
    }
}
