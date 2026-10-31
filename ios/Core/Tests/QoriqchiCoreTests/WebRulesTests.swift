import XCTest
@testable import QoriqchiCore

final class WebRulesTests: XCTestCase {

    private func level(_ url: String, _ db: ListKind? = nil) -> ThreatLevel? {
        WebRules.check(url) { _ in db }?.level
    }

    func testFakeBrandWithLureIsBlocked() {
        XCTAssertEqual(level("payme-bonus.top/login"), .malware)
        XCTAssertEqual(level("https://click-uz.com/karta"), .malware)
        XCTAssertEqual(level("http://uzcard-sovga.xyz/"), .malware)
        XCTAssertEqual(level("icloud-verify.top/"), .malware)
    }

    func testFakeBrandAloneIsSuspicious() {
        XCTAssertEqual(level("click-uz.com"), .suspicious)
        XCTAssertEqual(level("olx-dostavka.com"), .suspicious)
        XCTAssertEqual(level("paymeuz.com"), .suspicious)
    }

    func testOfficialSitesAreClean() {
        for url in [
            "payme.uz", "https://payme.uz/home", "click.uz", "my.gov.uz/oz/service/1", "kapitalbank.uz",
            "web.telegram.org", "t.me/durov", "olx.uz/d/obyavlenie", "google.com", "accounts.google.com/signin",
            "youtube.com/watch?v=1", "uzum.uz/uz/product/1", "www.icloud.com", "appleid.apple.com",
        ] {
            XCTAssertNil(level(url), url)
        }
    }

    func testBrandLikeWordsAreNotFalsePositives() {
        XCTAssertNil(level("paymentwall.com"))
        XCTAssertNil(level("clickbank.com"))
        XCTAssertNil(level("uzum-vino.uz"))
        XCTAssertNil(level("click-tracker.example.com"))
    }

    func testKnownBadHost() {
        let v = WebRules.check("http://asdzxcqwe.icu/gate") { _ in nil }
        XCTAssertEqual(v?.level, .malware)
        XCTAssertEqual(v?.host, "asdzxcqwe.icu")
        XCTAssertEqual(v?.reasons.first, "Imzolar bazasida bor: \"To'ydan arxivi\" troyanining serveri")
    }

    func testDatabaseHit() {
        XCTAssertEqual(level("innocent-looking.com", .phishing), .malware)
        XCTAssertEqual(level("files.example.net/a", .malware), .malware)
    }

    func testRawIpAndApk() {
        XCTAssertEqual(level("http://185.12.4.5/foto.apk"), .suspicious)
        XCTAssertNil(level("192.168.1.1"))
        XCTAssertNil(level("http://10.0.0.1/update"))
    }

    func testHomoglyphs() {
        XCTAssertEqual(level("xn--80ak6aa92e.com/login"), .suspicious)
        XCTAssertEqual(level("\u{0430}pple.com/login"), .suspicious) // kirill "а"
    }

    func testSearchQueriesAreIgnored() {
        XCTAssertNil(level("payme bonus olish"))
        XCTAssertNil(level("payme"))
        XCTAssertNil(level(""))
    }

    func testHostParsing() {
        XCTAssertEqual(WebRules.host(of: "https://user@payme-bonus.top:8443/x?y=1"), "payme-bonus.top")
        XCTAssertEqual(WebRules.host(of: "WWW.Example.COM/path"), "example.com")
        XCTAssertEqual(WebRules.host(of: "example.com."), "example.com")
        XCTAssertNil(WebRules.host(of: "localhost"))
        XCTAssertNil(WebRules.host(of: "qidiruv so'zi"))
    }

    func testSmsLinks() {
        let sms = "Hurmatli mijoz! Kartangiz bloklandi. Tiklash uchun: payme-bonus.top/login, 24 soat ichida."
        XCTAssertEqual(SmsRules.links(in: sms), ["payme-bonus.top/login"])
        XCTAssertEqual(SmsRules.verdict(for: sms) { _ in nil }?.level, .malware)

        XCTAssertNil(SmsRules.verdict(for: "Kod: 123456. Hech kimga bermang. payme.uz") { _ in nil })
        XCTAssertNil(SmsRules.verdict(for: "Salom, ertaga soat 5 da uchrashamiz") { _ in nil })
    }
}
