package uz.apkqoriqchi

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.math.ln

/**
 * Chuqur (qattiq) tahlil: APK ichini ochib, yangi, imzolar bazasida yo'q zararli variantlarni ham topadi.
 * Belgilar "To'ydan arxivi" namunasidan olingan: soxta ZIP yozuvlari, assets ichidagi shifrlangan yuk,
 * PackageInstaller orqali ikkinchi APK o'rnatish.
 */
object Deep {

    private val CORE_NAMES = setOf("AndroidManifest.xml", "classes.dex", "resources.arsc")

    /** Mashhur "qobiq" (packer) kutubxonalari — kodni antivirusdan yashirish uchun. */
    private val PACKER_LIB = Regex(
        "lib(jiagu|jgdtc|secexe|secmain|dexhelper|protectclass|shell[a-z0-9]*|apkprotect|tosprotection|mobisec|nqshield|baiduprotect|ddog|edog|bangcle)[^/]*\\.so$",
        RegexOption.IGNORE_CASE,
    )

    /** Firibgarlar arzon ro'yxatdan o'tkazadigan domen zonalari — faqat to'liq URL ichida hisoblanadi. */
    private val BAD_TLD = Regex(
        """(?:https?|wss?)://(?:[a-z0-9-]{1,63}\.)+(?:icu|top|xyz|cyou|buzz|tk|ml|ga|cf|gq|monster|rest|sbs|cfd)(?![a-z0-9.-])""",
        RegexOption.IGNORE_CASE,
    )

    private val DEX_NAME = Regex("""classes\d*\.dex""")

    /** Shifrlanmagan, lekin baribir "tasodifiy" ko'rinadigan formatlar (rasm, ovoz, arxiv) — ularni yuk deb hisoblamaymiz. */
    private val KNOWN_MAGIC = listOf(
        "89504e47", "ffd8ff", "474946", "52494646", "504b0304", "1f8b", "4f676753", "494433", "fff", "00010000",
        "4f54544f", "774f4646", "774f4632", "377abcaf", "fd377a58", "425a68", "28b52ffd", "556e697479",
        "6465780a", "7f454c46", "cafebabe", "1a45dfa3",
    )

    private const val PAYLOAD_MIN = 64 * 1024
    private const val DEX_LIMIT = 48L shl 20

    fun inspect(apk: File): List<Finding> {
        val zip = try {
            ZipFile(apk)
        } catch (e: Exception) {
            // Android o'rnatuvchisi ochadi, oddiy ZIP o'quvchi esa yo'q — ataylab buzilgan tuzilma.
            return listOf(Finding(4, "APK tuzilmasi ataylab buzilgan (tahlildan yashirinish)", Tag.EVASION))
        }
        return zip.use { inspectZip(it) }
    }

    private fun inspectZip(zip: ZipFile): List<Finding> {
        val out = mutableListOf<Finding>()
        val entries = zip.entries().toList()

        val fake = entries.count { e ->
            val parts = e.name.split('/')
            "//" in e.name || parts.dropLast(1).any { it in CORE_NAMES }
        }
        if (fake > 0) out += Finding(5, "APK ichi chalkashtirilgan: $fake ta soxta yozuv (antivirusdan yashirinish)", Tag.EVASION)

        val nestedApk = entries.firstOrNull { !it.isDirectory && it.name.endsWith(".apk", true) }
        if (nestedApk != null) out += Finding(4, "Ichida boshqa ilova yashirilgan: ${nestedApk.name}", Tag.PAYLOAD)

        val blob = entries.firstOrNull { it.name.startsWith("assets/") && isEncryptedBlob(zip, it) }
        if (blob != null && nestedApk == null) {
            out += Finding(3, "Shifrlangan yashirin yuk: ${blob.name.take(40)} (${Scanner.human(blob.size)})", Tag.PAYLOAD)
        }

        entries.firstOrNull { PACKER_LIB.containsMatchIn(it.name) }?.let {
            out += Finding(3, "Kodi shifrlangan qobiq ichida: ${it.name.substringAfterLast('/')}", Tag.PACKER)
        }

        val extraCode = entries.firstOrNull {
            !it.isDirectory && '/' in it.name && (it.name.endsWith(".dex", true) || it.name.endsWith(".jar", true))
        }
        out += dexFindings(zip, entries, extraCode)
        return out
    }

    private fun dexFindings(zip: ZipFile, entries: List<ZipEntry>, extraCode: ZipEntry?): List<Finding> {
        var telegramBot = false
        var dynamic = extraCode != null
        val domains = linkedSetOf<String>()
        var budget = DEX_LIMIT
        for (e in entries.filter { Regex("classes\\d*\\.dex").matches(it.name) }) {
            if (budget <= 0) break
            try {
                zip.getInputStream(e).use { input ->
                    budget -= scanChunks(input, budget) { chunk ->
                        if ("api.telegram.org" in chunk) telegramBot = true
                        if ("Ldalvik/system/DexClassLoader;" in chunk || "Ldalvik/system/InMemoryDexClassLoader;" in chunk) dynamic = true
                        if (domains.size < 3) BAD_TLD.findAll(chunk).forEach { domains += it.value }
                    }
                }
            } catch (ex: Exception) {
                // Buzilgan dex — keyingisiga o'tamiz.
            }
        }
        val out = mutableListOf<Finding>()
        if (telegramBot) out += Finding(4, "Ma'lumotlarni Telegram-bot orqali yuboradi", Tag.EXFIL)
        if (dynamic) {
            val where = extraCode?.let { ": ${it.name.take(40)}" } ?: ""
            out += Finding(2, "Ishlayotganda yashirin kod yuklaydi$where", Tag.DYNAMIC)
        }
        if (domains.isNotEmpty()) out += Finding(2, "Shubhali serverga ulanadi: ${domains.take(2).joinToString()}", Tag.C2)
        return out
    }

    /** Oqimni 1 MB bo'laklarda o'qiydi; bo'laklar chegarasidagi qatorlar yo'qolmasligi uchun ustma-ust qoldiriladi. */
    private fun scanChunks(input: InputStream, limit: Long, onChunk: (String) -> Unit): Long {
        val buf = ByteArray(1 shl 20)
        val overlap = 256
        var carry = ""
        var read = 0L
        while (read < limit) {
            var n = 0
            while (n < buf.size) {
                val r = input.read(buf, n, buf.size - n)
                if (r < 0) break
                n += r
            }
            if (n == 0) break
            read += n
            val text = carry + String(buf, 0, n, Charsets.ISO_8859_1)
            onChunk(text)
            carry = text.takeLast(overlap)
            if (n < buf.size) break
        }
        return read
    }

    private fun isEncryptedBlob(zip: ZipFile, e: ZipEntry): Boolean {
        if (e.isDirectory || e.size in 0 until PAYLOAD_MIN) return false
        return try {
            zip.getInputStream(e).use { input ->
                val sample = ByteArray(256 * 1024)
                var n = 0
                while (n < sample.size) {
                    val r = input.read(sample, n, sample.size - n)
                    if (r < 0) break
                    n += r
                }
                if (n < PAYLOAD_MIN) return false
                val head = sample.take(8).joinToString("") { "%02x".format(it) }.padEnd(16, '0')
                // MP4/MOV: 4-baytdan keyin "ftyp".
                if (KNOWN_MAGIC.any { head.startsWith(it) } || head.substring(8) == "66747970") return false
                entropy(sample, n) > 7.95
            }
        } catch (ex: Exception) {
            false
        }
    }

    /** Bayt boshiga Shennon entropiyasi: 8 ga yaqin bo'lsa — shifrlangan yoki siqilgan ma'lumot. */
    private fun entropy(data: ByteArray, len: Int): Double {
        val counts = IntArray(256)
        for (i in 0 until len) counts[data[i].toInt() and 0xFF]++
        var h = 0.0
        for (c in counts) {
            if (c == 0) continue
            val p = c.toDouble() / len
            h -= p * ln(p)
        }
        return h / ln(2.0)
    }
}
