import Foundation

/// Safari Content Blocker qoidalari (JSON). Safari bitta kengaytmada ~150 ming qoidani qabul qiladi,
/// regex'da "yoki" (`|`) va `\b` yo'q — shuning uchun har domen alohida qoida.
public enum BlockerRules {
    /// Brend qoidalari va zaxira uchun joy qoldiramiz.
    public static let maxDomainRules = 140_000

    /// [domains] — bazadagi domenlar (muhimlik tartibida: avval virus, keyin phishing).
    /// Bo'sh bo'lsa ham ichki qoidalar (ma'lum serverlar, soxta bank/to'lov saytlari) qoladi.
    public static func json(domains: [String]) -> Data {
        var rules: [String] = []
        rules.reserveCapacity(min(domains.count, maxDomainRules) + 64)
        for host in WebRules.knownBadHosts.keys.sorted() { rules.append(domainRule(host)) }
        rules.append(contentsOf: brandRules())
        for d in domains.prefix(maxDomainRules) { rules.append(domainRule(d)) }
        return Data(("[" + rules.joined(separator: ",") + "]").utf8)
    }

    /// Sayt va uning barcha subdomenlari, faqat sahifaning o'zi (rasm/skriptlar emas).
    static func domainRule(_ domain: String) -> String {
        let escaped = domain.replacingOccurrences(of: ".", with: "\\\\.")
        return #"{"trigger":{"url-filter":"^[^:]+://+([^:/]+\\.)?"# + escaped + #"[:/]","resource-type":["document"]},"action":{"type":"block"}}"#
    }

    /// Pul/davlat xizmatlari nomi bilan ochilgan, lekin rasmiy bo'lmagan saytlar: `payme-bonus.top`,
    /// `x.uzcard.click`. Nom alohida bo'lak bo'lishi kerak (`.payme-`), aks holda `paymentwall.com` ham tushardi.
    static func brandRules() -> [String] {
        WebRules.brands.filter { $0.money && !$0.context }.flatMap { b in
            let unless = b.official.flatMap { ["\"\($0)\"", "\"*\($0)\""] }.joined(separator: ",")
            return b.tokens.map { token in
                #"{"trigger":{"url-filter":"^[^:]+://+([^:/]*[.-])?"# + token + #"[.-]","resource-type":["document"],"unless-domain":["# +
                    unless + #"]},"action":{"type":"block"}}"#
            }
        }
    }
}
