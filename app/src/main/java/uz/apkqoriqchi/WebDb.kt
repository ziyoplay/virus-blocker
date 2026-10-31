package uz.apkqoriqchi

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.concurrent.thread

/**
 * Xavfli saytlar bazasi: ochiq ro'yxatlar kuniga bir marta yuklab olinadi va telefonda saqlanadi.
 * Har bir domen 64-bitli xesh sifatida saralangan massivda turadi (~170 ming domen ≈ 1.4 MB),
 * tekshiruv — ikkilik qidiruv. Tekshirilayotgan saytlar internetga yuborilmaydi.
 */
object WebDb {
    enum class Kind { PHISHING, MALWARE }

    internal class Feed(val id: String, val url: String, val kind: Kind)

    internal val FEEDS = listOf(
        // URLhaus (abuse.ch) — hozir virus tarqatayotgan saytlar
        Feed("urlhaus", "https://malware-filter.gitlab.io/malware-filter/urlhaus-filter-domains-online.txt", Kind.MALWARE),
        // PhishTank / OpenPhish / PhishStats asosidagi phishing ro'yxati
        Feed("phishing-filter", "https://malware-filter.gitlab.io/malware-filter/phishing-filter-domains.txt", Kind.PHISHING),
        Feed("phishing-army", "https://phishing.army/download/phishing_army_blocklist.txt", Kind.PHISHING),
    )

    private const val MAGIC = 0x51574231 // "QWB1"
    private const val MIN_ENTRIES = 10
    /** Mobil internetda katta ro'yxatlarni har kuni emas, 3 kunda bir yuklaymiz. */
    private const val METERED_MAX_AGE_MS = 72 * 3600 * 1000L
    private val DOMAIN = Regex("[a-z0-9.-]+\\.[a-z0-9-]+")

    @Volatile private var loaded: List<Pair<Kind, LongArray>>? = null

    private fun dir(ctx: Context) = File(ctx.filesDir, "webdb").apply { mkdirs() }
    private fun file(ctx: Context, f: Feed) = File(dir(ctx), "${f.id}.bin")

    fun exists(ctx: Context) = FEEDS.any { file(ctx, it).exists() }

    /** Bazadagi domenlar soni va oxirgi yangilanish vaqti. */
    fun stats(ctx: Context): Pair<Int, Long> {
        val files = FEEDS.map { file(ctx, it) }.filter { it.exists() }
        return files.sumOf { ((it.length() - 8) / 8).toInt() } to (files.maxOfOrNull { it.lastModified() } ?: 0L)
    }

    /** Sayt yoki uning yuqori domeni bazada bo'lsa — qaysi ro'yxatda ekani. */
    fun lookup(ctx: Context, host: String): Kind? = lookup(loaded ?: load(ctx), host)

    internal fun lookup(sets: List<Pair<Kind, LongArray>>, host: String): Kind? {
        var h = host
        while (true) {
            val key = hash(h)
            sets.firstOrNull { java.util.Arrays.binarySearch(it.second, key) >= 0 }?.let { return it.first }
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return null
            h = h.substring(dot + 1)
        }
    }

    @Synchronized
    fun load(ctx: Context): List<Pair<Kind, LongArray>> {
        loaded?.let { return it }
        val sets = FEEDS.mapNotNull { f -> read(file(ctx, f))?.let { f.kind to it } }
        loaded = sets
        return sets
    }

    internal fun read(file: File): LongArray? = try {
        file.takeIf { it.exists() }?.readBytes()?.let { bytes ->
            val buf = ByteBuffer.wrap(bytes)
            if (buf.int != MAGIC) return null
            LongArray(buf.int).also { buf.asLongBuffer().get(it) }
        }
    } catch (e: Exception) {
        null
    }

    /** Ro'yxatlarni yuklab oladi. Bittasi ishlamasa, uning eski nusxasi qoladi. true — hammasi muvaffaqiyatli. */
    fun update(ctx: Context, metered: Boolean): Boolean {
        var ok = true
        for (f in FEEDS) {
            val out = file(ctx, f)
            if (metered && out.exists() && System.currentTimeMillis() - out.lastModified() < METERED_MAX_AGE_MS) continue
            try {
                write(out, download(f.url))
            } catch (e: Exception) {
                ok = false
            }
        }
        loaded = null
        return ok
    }

    internal fun download(url: String): LongArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "APK-Qoriqchi")
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            var keys = LongArray(1 shl 16)
            var n = 0
            conn.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val d = parseLine(line) ?: continue
                    if (n == keys.size) keys = keys.copyOf(n * 2)
                    keys[n++] = hash(d)
                }
            }
            // Wi-Fi kirish sahifasi (captive portal) HTML qaytarsa — bazani buzmaymiz.
            if (n < MIN_ENTRIES) throw IOException("ro'yxat bo'sh")
            keys = keys.copyOf(n)
            keys.sort()
            return keys
        } finally {
            conn.disconnect()
        }
    }

    /** "domen", "0.0.0.0 domen", "||domen^" ko'rinishidagi qatorlar. */
    internal fun parseLine(line: String): String? {
        val s = line.trim()
        if (s.isEmpty() || s[0] == '#' || s[0] == '!') return null
        val parts = s.split(' ', '\t').filter { it.isNotEmpty() }
        val d = (if (parts.size > 1 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) parts[1] else parts[0])
            .removePrefix("||").removeSuffix("^").lowercase().removePrefix("www.")
        return d.takeIf { DOMAIN.matches(it) }
    }

    internal fun write(out: File, keys: LongArray) {
        val buf = ByteBuffer.allocate(8 + keys.size * 8)
        buf.putInt(MAGIC).putInt(keys.size)
        buf.asLongBuffer().put(keys)
        val tmp = File(out.path + ".tmp")
        tmp.writeBytes(buf.array())
        // Atomik almashtirish: o'qiyotgan xizmat hech qachon chala yozilgan faylni ko'rmaydi.
        Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** FNV-1a 64. */
    internal fun hash(s: String): Long {
        var h = -0x340d631b7bdddcdbL
        for (c in s) {
            h = h xor c.code.toLong()
            h *= 0x100000001b3L
        }
        return h
    }
}

/** Bazani kuniga bir marta (internet bo'lganda) yangilaydi. */
class WebDbJob : JobService() {

    companion object {
        private const val JOB_DAILY = 7001
        private const val JOB_NOW = 7002
        private const val DAY_MS = 24 * 3600 * 1000L

        fun schedule(ctx: Context, now: Boolean) {
            val js = ctx.getSystemService(JobScheduler::class.java)
            val component = ComponentName(ctx, WebDbJob::class.java)
            if (js.getPendingJob(JOB_DAILY) == null) {
                js.schedule(
                    JobInfo.Builder(JOB_DAILY, component)
                        .setPeriodic(DAY_MS)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPersisted(true)
                        .build(),
                )
            }
            if (now && js.getPendingJob(JOB_NOW) == null) {
                js.schedule(JobInfo.Builder(JOB_NOW, component).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build())
            }
        }

        fun cancel(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java)
            js.cancel(JOB_DAILY)
            js.cancel(JOB_NOW)
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        val metered = getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
        thread {
            val ok = WebDb.update(this, metered)
            jobFinished(params, !ok)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
