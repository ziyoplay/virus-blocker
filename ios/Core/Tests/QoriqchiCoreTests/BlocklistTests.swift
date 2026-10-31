import Foundation
import XCTest
@testable import QoriqchiCore

final class BlocklistTests: XCTestCase {

    func testParsesFeedFormats() {
        XCTAssertEqual(Blocklist.parseLine("evil.com"), "evil.com")
        XCTAssertEqual(Blocklist.parseLine("  www.Evil.COM  "), "evil.com")
        XCTAssertEqual(Blocklist.parseLine("0.0.0.0 evil.com"), "evil.com")
        XCTAssertEqual(Blocklist.parseLine("127.0.0.1\tevil.com"), "evil.com")
        XCTAssertEqual(Blocklist.parseLine("||evil.com^"), "evil.com")
        XCTAssertEqual(Blocklist.parseLine("1.92.101.221"), "1.92.101.221")
        XCTAssertNil(Blocklist.parseLine("# izoh"))
        XCTAssertNil(Blocklist.parseLine("! adblock izoh"))
        XCTAssertNil(Blocklist.parseLine(""))
        XCTAssertNil(Blocklist.parseLine("<html><head>"))
        XCTAssertNil(Blocklist.parseLine("localhost"))
    }

    /// Android'dagi Kotlin FNV-1a bilan bir xil natija (WebDb.hash("evil.com") qiymati).
    func testHashMatchesAndroid() {
        XCTAssertEqual(Blocklist.hash(""), 0xcbf2_9ce4_8422_2325)
        XCTAssertEqual(Blocklist.hash("a"), 0xaf63_dc4c_8601_ec8c)
    }

    func testEncodeDecodeLookup() {
        let keys = Blocklist.keys(["evil.com", "phish.example.net", "1.2.3.4", "evil.com"])
        XCTAssertEqual(keys.count, 3)
        let decoded = Blocklist.decode(Blocklist.encode(keys))
        XCTAssertEqual(decoded, keys)

        let sets: Blocklist.Sets = [(.phishing, decoded!)]
        XCTAssertEqual(Blocklist.lookup(sets, host: "evil.com"), .phishing)
        XCTAssertEqual(Blocklist.lookup(sets, host: "login.secure.evil.com"), .phishing)
        XCTAssertEqual(Blocklist.lookup(sets, host: "1.2.3.4"), .phishing)
        XCTAssertNil(Blocklist.lookup(sets, host: "example.net"))
        XCTAssertNil(Blocklist.lookup(sets, host: "notevil.com"))
        XCTAssertNil(Blocklist.lookup(sets, host: "evil.com.uz"))
    }

    func testCorruptDataIsIgnored() {
        XCTAssertNil(Blocklist.decode(Data("<html>captive portal</html>".utf8)))
        XCTAssertNil(Blocklist.decode(Data()))
    }

    func testBlockerJsonIsValid() throws {
        let data = BlockerRules.json(domains: ["evil.com", "1.2.3.4"])
        let rules = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [[String: Any]])
        // ma'lum server + brend qoidalari + 2 domen
        XCTAssertGreaterThan(rules.count, 3)
        let filters = rules.compactMap { ($0["trigger"] as? [String: Any])?["url-filter"] as? String }
        XCTAssertTrue(filters.contains(#"^[^:]+://+([^:/]+\.)?evil\.com[:/]"#))
        XCTAssertTrue(filters.contains(#"^[^:]+://+([^:/]+\.)?asdzxcqwe\.icu[:/]"#))

        // Brend qoidasi: soxtasini ushlaydi, rasmiysi va o'xshash so'z o'tadi
        let payme = try XCTUnwrap(rules.first { (($0["trigger"] as? [String: Any])?["url-filter"] as? String)?.contains("payme[") == true })
        let trigger = try XCTUnwrap(payme["trigger"] as? [String: Any])
        let re = try NSRegularExpression(pattern: try XCTUnwrap(trigger["url-filter"] as? String))
        func hit(_ s: String) -> Bool { re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) != nil }
        XCTAssertTrue(hit("https://payme-bonus.top/login"))
        XCTAssertTrue(hit("https://login.payme.click/"))
        XCTAssertFalse(hit("https://paymentwall.com/"))
        XCTAssertEqual(trigger["unless-domain"] as? [String], ["payme.uz", "*payme.uz", "paycom.uz", "*paycom.uz"])
    }

    func testBlockerJsonRespectsLimit() {
        let many = (0 ..< BlockerRules.maxDomainRules + 10).map { "d\($0).example" }
        let data = BlockerRules.json(domains: many)
        let rules = (try? JSONSerialization.jsonObject(with: data)) as? [Any]
        XCTAssertLessThanOrEqual(rules?.count ?? .max, BlockerRules.maxDomainRules + 100)
    }

    /// Haqiqiy ro'yxatlar yuklanadimi va ulardagi domenlar topiladimi. Internet bo'lmasa — o'tkazib yuboriladi.
    func testRealFeeds() async throws {
        for feed in Blocklist.feeds {
            let domains: [String]
            do {
                domains = try await Blocklist.download(feed)
            } catch {
                throw XCTSkip("internet yo'q yoki \(feed.id) ishlamayapti: \(error)")
            }
            XCTAssertGreaterThan(domains.count, 500, feed.id)
            let sets: Blocklist.Sets = [(feed.kind, Blocklist.keys(domains))]
            for d in domains.dropFirst(100).prefix(20) {
                XCTAssertEqual(Blocklist.lookup(sets, host: d), feed.kind, "\(feed.id): \(d)")
            }
            print("\(feed.id): \(domains.count) domen")
        }
    }
}
