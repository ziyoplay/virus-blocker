import Foundation

public enum ThreatLevel: Int, Comparable {
    case suspicious = 1
    case malware = 2

    public static func < (a: ThreatLevel, b: ThreatLevel) -> Bool { a.rawValue < b.rawValue }
}

public enum ListKind: String {
    case phishing, malware
}

public struct SiteVerdict {
    public let url: String
    public let host: String
    public let level: ThreatLevel
    public let score: Int
    public let reasons: [String]
}

/// Sayt manzilini tekshirish: xavfli saytlar bazasi + evristika (soxta bank/to'lov saytlari, IP-manzil,
/// o'xshash harflar, shubhali zonalar, APK yuklash). Android'dagi `WebGuard` bilan bir xil qoidalar.
public enum WebRules {
    public static let dangerScore = 8
    public static let suspiciousScore = 5

    /// O'zimiz tahlil qilgan namunalardagi serverlar.
    public static let knownBadHosts = [
        "asdzxcqwe.icu": "\"To'ydan arxivi\" troyanining serveri",
    ]

    /// [context] — oddiy so'z ham bo'lishi mumkin bo'lgan nomlar ("click", "uzum"): ular faqat yonida
    /// "uz", "pay", "bank" yoki aldamchi so'z bo'lsa hisoblanadi. [money] — pul/davlat xizmati: Safari'da
    /// bunday soxta saytlar evristika bo'yicha ham bloklanadi.
    public struct Brand {
        public let name: String
        public let tokens: [String]
        public let official: [String]
        public let context: Bool
        public let money: Bool
    }

    public static let brands: [Brand] = [
        Brand(name: "Payme", tokens: ["payme", "paycom"], official: ["payme.uz", "paycom.uz"], context: false, money: true),
        Brand(name: "Click", tokens: ["click"], official: ["click.uz"], context: true, money: true),
        Brand(name: "Uzcard", tokens: ["uzcard"], official: ["uzcard.uz"], context: false, money: true),
        Brand(name: "Humo", tokens: ["humo", "humocard"], official: ["humocard.uz", "humo.uz"], context: true, money: true),
        Brand(name: "Uzum", tokens: ["uzum", "uzumbank", "apelsin"], official: ["uzum.uz", "uzumbank.uz", "uzum.com", "apelsin.uz"], context: true, money: true),
        Brand(name: "Paynet", tokens: ["paynet"], official: ["paynet.uz"], context: false, money: true),
        Brand(name: "Kapitalbank", tokens: ["kapitalbank"], official: ["kapitalbank.uz"], context: false, money: true),
        Brand(name: "Hamkorbank", tokens: ["hamkorbank"], official: ["hamkorbank.uz"], context: false, money: true),
        Brand(name: "Ipak Yo'li", tokens: ["ipakyulibank", "ipakyuli"], official: ["ipakyulibank.uz"], context: false, money: true),
        Brand(name: "Agrobank", tokens: ["agrobank"], official: ["agrobank.uz"], context: false, money: true),
        Brand(name: "Xalq banki", tokens: ["xalqbank", "xalqbanki"], official: ["xb.uz", "xalqbank.uz"], context: false, money: true),
        Brand(name: "Anorbank", tokens: ["anorbank"], official: ["anorbank.uz"], context: false, money: true),
        Brand(name: "Asakabank", tokens: ["asakabank"], official: ["asakabank.uz"], context: false, money: true),
        Brand(name: "TBC Bank", tokens: ["tbcbank"], official: ["tbcbank.uz", "tbcbank.ge"], context: false, money: true),
        Brand(name: "my.gov.uz", tokens: ["mygov"], official: ["my.gov.uz"], context: false, money: true),
        Brand(name: "Soliq", tokens: ["soliq"], official: ["soliq.uz"], context: false, money: true),
        Brand(name: "O'zbekiston pochtasi", tokens: ["pochta", "uzpost"], official: ["pochta.uz"], context: true, money: false),
        Brand(name: "OLX", tokens: ["olx"], official: ["olx.uz", "olx.kz", "olx.ua", "olx.pl", "olx.ro", "olx.pt", "olx.bg", "olx.in", "olx.com", "olx.com.br"], context: false, money: false),
        Brand(name: "Telegram", tokens: ["telegram"], official: ["telegram.org", "telegram.me", "t.me", "telegra.ph", "telesco.pe"], context: false, money: false),
        Brand(name: "WhatsApp", tokens: ["whatsapp"], official: ["whatsapp.com", "whatsapp.net", "wa.me"], context: false, money: false),
        Brand(name: "Instagram", tokens: ["instagram"], official: ["instagram.com"], context: false, money: false),
        Brand(name: "Facebook", tokens: ["facebook"], official: ["facebook.com", "fb.com", "messenger.com"], context: false, money: false),
        Brand(name: "Steam", tokens: ["steamcommunity", "steampowered"], official: ["steamcommunity.com", "steampowered.com"], context: false, money: false),
        Brand(name: "Sberbank", tokens: ["sberbank", "sber"], official: ["sberbank.ru", "sber.ru", "sberbank.com"], context: false, money: true),
        Brand(name: "Kaspi", tokens: ["kaspi"], official: ["kaspi.kz"], context: false, money: true),
        Brand(name: "Tinkoff", tokens: ["tinkoff"], official: ["tinkoff.ru", "tbank.ru"], context: false, money: true),
        Brand(name: "Apple", tokens: ["icloud", "appleid"], official: ["icloud.com", "apple.com"], context: false, money: true),
    ]

    /// "paymeuz", "mypayme" kabi yopishtirilgan qismlar ham brend deb sanaladi.
    static let glued: Set<String> = ["uz", "uzb", "app", "pay", "bonus", "login", "online", "id", "gift", "karta", "card", "auth", "verify", "secure", "web", "my"]
    static let contextTokens: Set<String> = ["uz", "uzb", "pay", "bank", "karta", "card"]

    static let lure = try! NSRegularExpression(
        pattern: "bonus|yutuq|sovg.?a|prize|gift|free|login|signin|verif|kompensat|to.?lov|karta|card|secure|account|update|" +
            "konkurs|aksiya|viplat|keshbek|cashback|giveaway|выплат|компенсац|приз|подар|бонус|розыгр"
    )

    public static let suspiciousTLDs: Set<String> = [
        "icu", "top", "xyz", "tk", "ml", "ga", "cf", "gq", "buzz", "rest", "cyou", "monster", "sbs", "cfd", "lol", "quest", "bond", "click",
    ]

    /// Bepul hosting va tunnellar: firibgarlar sayt ochishga eng ko'p shulardan foydalanadi.
    static let freeHosting = [
        "pages.dev", "workers.dev", "vercel.app", "netlify.app", "glitch.me", "web.app", "firebaseapp.com", "000webhostapp.com",
        "weebly.com", "wixsite.com", "github.io", "replit.dev", "onrender.com", "ngrok-free.app", "ngrok.io", "ngrok.app",
        "trycloudflare.com", "loca.lt", "serveo.net", "tilda.ws", "blogspot.com",
    ]

    static let ipv4 = try! NSRegularExpression(pattern: #"^\d{1,3}(\.\d{1,3}){3}$"#)
    static let privateIP = try! NSRegularExpression(pattern: #"^((10|127)\..*|192\.168\..*|172\.(1[6-9]|2\d|3[01])\..*)$"#)

    public static func check(_ raw: String, lookup: (String) -> ListKind?) -> SiteVerdict? {
        guard let host = host(of: raw) else { return nil }
        let url = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let lower = url.lowercased()
        var found: [(Int, String)] = []

        if let why = knownBadHosts[host] { found.append((20, "Imzolar bazasida bor: \(why)")) }
        switch lookup(host) {
        case .phishing: found.append((20, "Firibgar (phishing) saytlar bazasida bor"))
        case .malware: found.append((20, "Virus tarqatuvchi saytlar bazasida bor"))
        case nil: break
        }

        let labels = host.split(separator: ".").map(String.init)
        let tokens = labels.dropLast().flatMap { $0.split(separator: "-").map(String.init) }
        let lureWord = firstMatch(lure, in: lower)
        if let b = brand(of: host, tokens: tokens, lure: lureWord != nil) {
            found.append((7, "O'zini \"\(b.name)\" qilib ko'rsatadi, lekin rasmiy sayti emas (rasmiysi: \(b.official[0]))"))
        }
        if let w = lureWord { found.append((2, "Manzilda aldamchi so'z: \"\(w)\"")) }
        if matches(ipv4, host) && !matches(privateIP, host) { found.append((4, "Sayt nomi o'rniga faqat IP-manzil")) }
        if labels.contains(where: { $0.hasPrefix("xn--") }) || host.unicodeScalars.contains(where: { !$0.isASCII }) {
            found.append((4, "Harflari almashtirilgan manzil (o'xshash harflar bilan aldash)"))
        }
        if let tld = labels.last, suspiciousTLDs.contains(tld) { found.append((2, "Firibgarlar ko'p ishlatadigan domen zonasi: .\(tld)")) }
        if let h = freeHosting.first(where: { host.hasSuffix("." + $0) }) { found.append((1, "Bepul hostingda joylashgan: \(h)")) }
        if authority(of: lower).contains("@") { found.append((4, "Manzil ichida yashirin nom (@) bor")) }
        if host.filter({ $0 == "-" }).count >= 4 || labels.count >= 6 { found.append((1, "G'alati uzun manzil")) }
        if let r = lower.range(of: host) {
            let path = lower[r.upperBound...].split(separator: "?", maxSplits: 1, omittingEmptySubsequences: false)[0]
                .split(separator: "#", maxSplits: 1, omittingEmptySubsequences: false)[0]
            if path.hasSuffix(".apk") { found.append((3, "Sayt telefonga APK fayl (ilova) yuklatmoqchi")) }
        }

        let score = found.reduce(0) { $0 + $1.0 }
        let level: ThreatLevel
        if score >= dangerScore {
            level = .malware
        } else if score >= suspiciousScore {
            level = .suspicious
        } else {
            return nil
        }
        let reasons = found.enumerated().sorted { ($0.element.0, -$0.offset) > ($1.element.0, -$1.offset) }.map { $0.element.1 }
        return SiteVerdict(url: url, host: host, level: level, score: score, reasons: reasons)
    }

    static func brand(of host: String, tokens: [String], lure: Bool) -> Brand? {
        for b in brands {
            if b.official.contains(where: { host == $0 || host.hasSuffix("." + $0) }) { continue }
            let hit = tokens.contains { t in
                b.tokens.contains { brand in
                    t == brand ||
                        (t.hasPrefix(brand) && glued.contains(String(t.dropFirst(brand.count)))) ||
                        (t.hasSuffix(brand) && glued.contains(String(t.dropLast(brand.count))))
                }
            }
            if !hit { continue }
            if b.context && !lure && !tokens.contains(where: { contextTokens.contains($0) }) &&
                !tokens.contains(where: { t in b.tokens.contains { t != $0 && t.hasPrefix($0) } }) {
                continue
            }
            return b
        }
        return nil
    }

    static func authority(of lower: String) -> Substring {
        let afterScheme = lower.range(of: "://").map { lower[$0.upperBound...] } ?? lower[...]
        return afterScheme.prefix { $0 != "/" && $0 != "?" && $0 != "#" }
    }

    /// Manzil satridagi matndan sayt nomini ajratadi; qidiruv so'zi bo'lsa — nil.
    public static func host(of raw: String) -> String? {
        let s = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if s.isEmpty || s.contains(where: { $0.isWhitespace }) { return nil }
        var host = authority(of: s)
        if let at = host.lastIndex(of: "@") { host = host[host.index(after: at)...] }
        host = host.prefix { $0 != ":" }
        while host.hasSuffix(".") { host = host.dropLast() }
        guard host.contains("."), !host.hasPrefix("."), !host.contains("..") else { return nil }
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: ".-"))
        guard host.unicodeScalars.allSatisfy(allowed.contains) else { return nil }
        let h = String(host)
        return h.hasPrefix("www.") ? String(h.dropFirst(4)) : h
    }

    static func matches(_ re: NSRegularExpression, _ s: String) -> Bool {
        re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) != nil
    }

    static func firstMatch(_ re: NSRegularExpression, in s: String) -> String? {
        guard let m = re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)), let r = Range(m.range, in: s) else { return nil }
        return String(s[r])
    }
}
