package uz.apkqoriqchi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebGuardTest {

    private fun level(url: String, db: WebDb.Kind? = null) = WebGuard.check(url) { db }?.level

    @Test
    fun fakeBrandWithLureIsBlocked() {
        assertEquals(ThreatLevel.MALWARE, level("payme-bonus.top/login"))
        assertEquals(ThreatLevel.MALWARE, level("https://click-uz.com/karta"))
        assertEquals(ThreatLevel.MALWARE, level("http://uzcard-sovga.xyz/"))
        assertEquals(ThreatLevel.MALWARE, level("icloud-verify.top/"))
    }

    @Test
    fun fakeBrandAloneIsSuspicious() {
        assertEquals(ThreatLevel.SUSPICIOUS, level("click-uz.com"))
        assertEquals(ThreatLevel.SUSPICIOUS, level("olx-dostavka.com"))
        assertEquals(ThreatLevel.SUSPICIOUS, level("paymeuz.com"))
    }

    @Test
    fun officialSitesAreClean() {
        for (url in listOf(
            "payme.uz", "https://payme.uz/home", "click.uz", "my.gov.uz/oz/service/1", "kapitalbank.uz",
            "web.telegram.org", "t.me/durov", "olx.uz/d/obyavlenie", "google.com", "accounts.google.com/signin",
            "youtube.com/watch?v=1", "uzum.uz/uz/product/1", "www.icloud.com", "appleid.apple.com",
        )) {
            assertNull(url, level(url))
        }
    }

    @Test
    fun brandLikeWordsAreNotFalsePositives() {
        // "payme" ichida, lekin boshqa so'z
        assertNull(level("paymentwall.com"))
        assertNull(level("clickbank.com"))
        // "uzum" — uzum (meva): yonida "uz/pay/bank" yoki aldamchi so'z yo'q
        assertNull(level("uzum-vino.uz"))
        assertNull(level("click-tracker.example.com"))
    }

    @Test
    fun knownBadHostIsBlocked() {
        val v = WebGuard.check("http://asdzxcqwe.icu/gate") { null }!!
        assertEquals(ThreatLevel.MALWARE, v.level)
        assertEquals("asdzxcqwe.icu", v.host)
    }

    @Test
    fun databaseHitIsBlocked() {
        assertEquals(ThreatLevel.MALWARE, level("innocent-looking.com", WebDb.Kind.PHISHING))
        assertEquals(ThreatLevel.MALWARE, level("files.example.net/a", WebDb.Kind.MALWARE))
    }

    @Test
    fun rawIpAndApk() {
        assertEquals(ThreatLevel.SUSPICIOUS, level("http://185.12.4.5/foto.apk"))
        // Uy routeri — ogohlantirish yo'q
        assertNull(level("192.168.1.1"))
        assertNull(level("http://10.0.0.1/update"))
    }

    @Test
    fun punycodeWithLure() {
        assertEquals(ThreatLevel.SUSPICIOUS, level("xn--80ak6aa92e.com/login"))
        // Unicode ko'rinishida kelsa ham punycode'ga aylantiriladi
        assertEquals(ThreatLevel.SUSPICIOUS, level("аpple.com/login")) // kirill "а"
    }

    @Test
    fun searchQueriesAreIgnored() {
        assertNull(level("payme bonus olish"))
        assertNull(level("payme"))
        assertNull(level(""))
    }

    @Test
    fun hostParsing() {
        assertEquals("payme-bonus.top", WebGuard.hostOf("https://user@payme-bonus.top:8443/x?y=1"))
        assertEquals("example.com", WebGuard.hostOf("WWW.Example.COM/path"))
        assertEquals("example.com", WebGuard.hostOf("example.com."))
        assertNull(WebGuard.hostOf("localhost"))
        assertNull(WebGuard.hostOf("qidiruv so'zi"))
    }
}
