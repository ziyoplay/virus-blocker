package uz.apkqoriqchi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import java.io.File
import java.net.URL

class WebDbTest {

    @Test
    fun parsesFeedFormats() {
        assertEquals("evil.com", WebDb.parseLine("evil.com"))
        assertEquals("evil.com", WebDb.parseLine("  www.Evil.COM  "))
        assertEquals("evil.com", WebDb.parseLine("0.0.0.0 evil.com"))
        assertEquals("evil.com", WebDb.parseLine("127.0.0.1\tevil.com"))
        assertEquals("evil.com", WebDb.parseLine("||evil.com^"))
        assertEquals("1.92.101.221", WebDb.parseLine("1.92.101.221"))
        assertNull(WebDb.parseLine("# izoh"))
        assertNull(WebDb.parseLine("! adblock izoh"))
        assertNull(WebDb.parseLine(""))
        // Wi-Fi kirish sahifasi HTML qaytarsa
        assertNull(WebDb.parseLine("<html><head>"))
        assertNull(WebDb.parseLine("localhost"))
    }

    @Test
    fun writeReadLookup() {
        val keys = listOf("evil.com", "phish.example.net", "1.2.3.4").map(WebDb::hash).toLongArray().apply { sort() }
        val file = File.createTempFile("webdb", ".bin")
        try {
            WebDb.write(file, keys)
            val read = WebDb.read(file)!!
            assertArrayEquals(keys, read)

            val sets = listOf(WebDb.Kind.PHISHING to read)
            assertEquals(WebDb.Kind.PHISHING, WebDb.lookup(sets, "evil.com"))
            // Subdomen ham ushlanadi
            assertEquals(WebDb.Kind.PHISHING, WebDb.lookup(sets, "login.secure.evil.com"))
            assertEquals(WebDb.Kind.PHISHING, WebDb.lookup(sets, "1.2.3.4"))
            // Yuqori domen ro'yxatda bo'lmasa — pastkisi ham bloklanmaydi
            assertNull(WebDb.lookup(sets, "example.net"))
            assertNull(WebDb.lookup(sets, "notevil.com"))
            assertNull(WebDb.lookup(sets, "evil.com.uz"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun corruptFileIsIgnored() {
        val file = File.createTempFile("webdb", ".bin")
        try {
            file.writeText("<html>captive portal</html>")
            assertNull(WebDb.read(file))
        } finally {
            file.delete()
        }
    }

    /** Haqiqiy ro'yxatlar: manzillar ishlayaptimi va yuklangan baza ulardagi domenlarni topadimi. Internet kerak. */
    @Test
    fun realFeedsDownloadAndMatch() {
        for (feed in WebDb.FEEDS) {
            val keys = try {
                WebDb.download(feed.url)
            } catch (e: java.io.IOException) {
                assumeNoException("internet yo'q yoki ${feed.id} ishlamayapti", e)
                return
            }
            assertTrue("${feed.id}: juda kam yozuv (${keys.size})", keys.size > 500)
            assertTrue("${feed.id}: saralanmagan", (1 until keys.size).all { keys[it - 1] <= keys[it] })

            val sample = URL(feed.url).openStream().bufferedReader().useLines { lines ->
                lines.mapNotNull(WebDb::parseLine).drop(100).take(20).toList()
            }
            val sets = listOf(feed.kind to keys)
            for (d in sample) assertEquals("${feed.id}: $d", feed.kind, WebDb.lookup(sets, d))
            println("${feed.id}: ${keys.size} domen")
        }
    }
}
