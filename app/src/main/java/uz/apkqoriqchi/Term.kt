package uz.apkqoriqchi

import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowInsets

object Palette {
    val GREEN = 0xFF00FF41.toInt()
    val GREEN_DIM = 0xFF0A8F2E.toInt()
    val BRIGHT = 0xFFC8FFD4.toInt()
    val GRAY = 0xFF4F5F55.toInt()
    val RED = 0xFFFF2B2B.toInt()
    val RED_DIM = 0xFFC23A3A.toInt()
    val YELLOW = 0xFFFFC400.toInt()
    val YELLOW_DIM = 0xFFB08A10.toInt()
    val CYAN = 0xFF00E5FF.toInt()
    val BG_TERM = 0xFF020A04.toInt()
}

/** Skaner terminalga shu interfeys orqali yozadi (ishchi oqimdan chaqiriladi). */
interface Console {
    fun line(text: String = "", color: Int = Palette.GREEN, pause: Long = 0)
    fun progress(done: Int, total: Int)
    /** Bekor qilingan bo'lsa CancellationException tashlaydi. */
    fun check()
    /** Yangi bosqich boshlandi (oddiy tildagi sarlavha). */
    fun stage(title: String) {}
    /** Hozir tekshirilayotgan obyekt nomi. */
    fun item(name: String) {}
}

/** Hech narsa chiqarmaydigan konsol (fon rejimidagi tekshiruv uchun). */
object SilentConsole : Console {
    override fun line(text: String, color: Int, pause: Long) {}
    override fun progress(done: Int, total: Int) {}
    override fun check() {}
}

object Art {
    val MALWARE = listOf(
        " █▀▄▀█ ▄▀█ █░░ █░█░█ ▄▀█ █▀█ █▀▀",
        " █░▀░█ █▀█ █▄▄ ▀▄▀▄▀ █▀█ █▀▄ ██▄",
    )
    val CLEAN = listOf(
        " █▀▀ █░░ █▀▀ ▄▀█ █▄░█",
        " █▄▄ █▄▄ ██▄ █▀█ █░▀█",
    )
    val PURGED = listOf(
        " █▀█ █░█ █▀█ █▀▀ █▀▀ █▀▄",
        " █▀▀ █▄█ █▀▄ █▄█ ██▄ █▄▀",
    )

    fun bar(done: Int, total: Int, width: Int = 18): String {
        val filled = if (total <= 0) width else (done * width / total).coerceIn(0, width)
        return "█".repeat(filled) + "░".repeat(width - filled)
    }
}

object Ui {
    /** targetSdk 35+ da ekran chetma-chet chiziladi — status/navigatsiya paneli uchun joy qoldiramiz. */
    fun applySystemBarInsets(root: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val l = root.paddingLeft
        val t = root.paddingTop
        val r = root.paddingRight
        val b = root.paddingBottom
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
            insets
        }
    }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("qoriqchi", Context.MODE_PRIVATE)

    var lastScan: Long
        get() = sp.getLong("last_scan", 0)
        set(v) = sp.edit().putLong("last_scan", v).apply()

    /** Oxirgi skanerdan keyin qolgan (o'chirilmagan) tahdidlar soni. */
    var openThreats: Int
        get() = sp.getInt("open_threats", 0)
        set(v) = sp.edit().putInt("open_threats", v).apply()

    var totalRemoved: Int
        get() = sp.getInt("total_removed", 0)
        set(v) = sp.edit().putInt("total_removed", v).apply()

    var realtime: Boolean
        get() = sp.getBoolean("realtime", false)
        set(v) = sp.edit().putBoolean("realtime", v).apply()

    var autostartHintShown: Boolean
        get() = sp.getBoolean("autostart_hint", false)
        set(v) = sp.edit().putBoolean("autostart_hint", v).apply()

    /** Foydalanuvchi "bilaman, qoldirish" degan paketlar / fayllar. */
    var trusted: Set<String>
        get() = sp.getStringSet("trusted", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("trusted", v).apply()
}
