package uz.apkqoriqchi

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.Locale
import java.util.Random
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class ScanActivity : Activity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_FULL = 0
        const val MODE_APPS = 1
        const val MODE_FILES = 2

        private const val REQ_UNINSTALL = 11
        private const val MAX_LOG_CHARS = 80_000
    }

    private lateinit var header: TextView
    private lateinit var statusLine: TextView
    private lateinit var frame: FrameLayout
    private lateinit var scroll: ScrollView
    private lateinit var logView: TextView
    private lateinit var progressView: TextView
    private lateinit var btnDelete: TextView
    private lateinit var btnMenu: TextView

    private lateinit var prefs: Prefs
    private var mode = MODE_FULL
    private var threats: List<Threat> = emptyList()
    private var worker: Thread? = null
    private var blink: ObjectAnimator? = null
    private var waitingForStorageSettings = false
    private var lastProgressPct = -1

    @Volatile private var cancelled = false
    @Volatile private var uninstallLatch: CountDownLatch? = null

    private val console = object : Console {
        override fun line(text: String, color: Int, pause: Long) {
            check()
            runOnUiThread { append(text, color) }
            if (pause > 0) Thread.sleep(pause)
        }

        override fun progress(done: Int, total: Int) {
            val pct = if (total == 0) 100 else done * 100 / total
            if (pct == lastProgressPct) return
            lastProgressPct = pct
            val text = String.format(Locale.US, "[%s] %3d%%  %d/%d", Art.bar(done, total), pct, done, total)
            runOnUiThread { progressView.text = text }
        }

        override fun check() {
            if (cancelled) throw CancellationException()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        Ui.applySystemBarInsets(findViewById(R.id.root))

        header = findViewById(R.id.header)
        statusLine = findViewById(R.id.statusLine)
        frame = findViewById(R.id.frame)
        scroll = findViewById(R.id.scroll)
        logView = findViewById(R.id.log)
        progressView = findViewById(R.id.progress)
        btnDelete = findViewById(R.id.btnDelete)
        btnMenu = findViewById(R.id.btnMenu)
        logView.setText("", TextView.BufferType.EDITABLE)

        prefs = Prefs(this)
        mode = intent.getIntExtra(EXTRA_MODE, MODE_FULL)
        btnMenu.setOnClickListener { finish() }
        btnDelete.setOnClickListener { confirmPurge() }

        val cmd = when (mode) {
            MODE_APPS -> "scan --apps"
            MODE_FILES -> "scan --files --apk"
            else -> "scan --full --deep"
        }
        header.text = "root@qoriqchi:~# ./$cmd"
        applyTheme(Palette.GREEN)
        setStatus("[ TAYYORLANMOQDA ]", Palette.GREEN, false)

        if (mode != MODE_APPS && !hasStorage()) requestStorage() else startScan()
    }

    override fun onResume() {
        super.onResume()
        if (waitingForStorageSettings) {
            waitingForStorageSettings = false
            startScan()
        }
    }

    override fun onDestroy() {
        cancelled = true
        uninstallLatch?.countDown()
        worker?.interrupt()
        blink?.cancel()
        super.onDestroy()
    }

    // ───────────────────────── RUXSATLAR ─────────────────────────

    private fun hasStorage(): Boolean = Storage.has(this)

    private fun requestStorage() {
        if (!Storage.needsSettingsScreen) {
            requestPermissions(Storage.LEGACY_PERMISSIONS, Storage.REQUEST_CODE)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("> Fayllarga ruxsat")
            .setMessage("APK fayllarni topish va zararlisini o'chirish uchun \"Barcha fayllarga ruxsat\" kerak.\n\nKeyingi oynada APK Qo'riqchi uchun ruxsatni yoqing va orqaga qayting.")
            .setCancelable(false)
            .setPositiveButton("RUXSAT BERISH") { _, _ ->
                waitingForStorageSettings = true
                val opened = Storage.settingsIntents(this).any {
                    try {
                        startActivity(it)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                if (!opened) startScan()
            }
            .setNegativeButton("O'TKAZIB YUBORISH") { _, _ -> startScan() }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Storage.REQUEST_CODE) startScan()
    }

    // ───────────────────────── SKANERLASH ─────────────────────────

    private fun startScan() {
        if (worker != null) return
        setStatus("[ SKANERLANMOQDA... ]", Palette.GREEN, true)
        worker = thread(name = "scanner") {
            try {
                val started = SystemClock.elapsedRealtime()
                intro()
                val scanner = Scanner(this, console)
                val found = mutableListOf<Threat>()
                if (mode != MODE_FILES) found += scanner.scanApps()
                if (mode != MODE_APPS) {
                    if (hasStorage()) found += scanner.scanFiles()
                    else console.line("[!] Fayllarga ruxsat yo'q — fayl skaneri o'tkazib yuborildi", Palette.YELLOW, 150)
                }
                if (mode == MODE_FULL) scanner.deviceChecks()
                summary(found, scanner, SystemClock.elapsedRealtime() - started)
                runOnUiThread { showResult(found) }
            } catch (_: CancellationException) {
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun intro() {
        val rnd = Random()
        console.line("APK QO'RIQCHI :: anti-malware engine v1.0", Palette.BRIGHT, 150)
        console.line("[*] Qurilma : ${Build.MANUFACTURER} ${Build.MODEL}", Palette.GREEN, 90)
        console.line("[*] Tizim   : Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", Palette.GREEN, 90)
        console.line("[*] Yamoq   : ${Build.VERSION.SECURITY_PATCH}", Palette.GREEN, 90)
        console.line("[*] Evristik dvigatel yuklanmoqda...", Palette.GREEN, 120)
        repeat(5) {
            val addr = String.format(Locale.US, "0x%08x", 0x7f000000 + rnd.nextInt(0xffffff))
            val hex = (1..8).joinToString(" ") { String.format(Locale.US, "%02x", rnd.nextInt(256)) }
            console.line("    $addr  $hex", Palette.GRAY, 45)
        }
        console.line("[+] Qoidalar: SMS-troyan, bank-troyan, soxta ilova, yashirin ikonka, EICAR", Palette.GREEN, 150)
        console.line("[+] Deep: soxta ZIP yozuvlari, shifrlangan yuk, packer, Telegram-bot, C2 domenlar", Palette.GREEN, 150)
    }

    private fun summary(found: List<Threat>, s: Scanner, ms: Long) {
        val mal = found.count { it.level == ThreatLevel.MALWARE }
        val sus = found.size - mal
        val c = if (found.isEmpty()) Palette.GREEN else Palette.RED
        console.line()
        console.line("════════════════════════════════════", c, 100)
        console.line(" SKANERLASH YAKUNLANDI  (${ms / 1000}.${ms % 1000 / 100} s)", c, 100)
        console.line(" ilovalar: ${s.appsScanned}  |  fayllar: ${s.filesScanned}", c, 100)
        console.line(" malware: $mal  |  shubhali: $sus", c, 100)
        console.line("════════════════════════════════════", c, 200)
        console.line()
        if (found.isEmpty()) {
            Art.CLEAN.forEach { console.line(it, Palette.GREEN, 80) }
            console.line()
            console.line("[✓] Hech qanday tahdid topilmadi. Telefon toza.", Palette.GREEN)
            return
        }
        Art.MALWARE.forEach { console.line(it, Palette.RED, 80) }
        console.line()
        console.line("TAHDIDLAR RO'YXATI:", Palette.RED, 100)
        found.forEachIndexed { i, t ->
            val c2 = if (t.level == ThreatLevel.MALWARE) Palette.RED else Palette.YELLOW
            val kind = if (t.kind == ThreatKind.APP) "ilova" else "fayl"
            console.line(" ${i + 1}. [${t.level.name}] ${t.family}", c2, 60)
            console.line("    $kind: ${t.target}", Palette.RED_DIM, 40)
        }
        console.line()
        console.line("[!] Pastdagi O'CHIRISH tugmasini bosing", Palette.RED)
    }

    private fun showResult(found: List<Threat>) {
        threats = found
        prefs.lastScan = System.currentTimeMillis()
        prefs.openThreats = found.size
        worker = null
        btnMenu.visibility = View.VISIBLE
        if (found.isEmpty()) {
            applyTheme(Palette.GREEN)
            setStatus("[✓] TAHDID TOPILMADI", Palette.GREEN, false)
            return
        }
        val mal = found.count { it.level == ThreatLevel.MALWARE }
        applyTheme(Palette.RED)
        setStatus(if (mal > 0) "☠ MALWARE: $mal | JAMI: ${found.size} ☠" else "⚠ SHUBHALI: ${found.size} ⚠", Palette.RED, true)
        btnDelete.text = "[ ☠ HAMMASINI O'CHIRISH (${found.size}) ]"
        btnDelete.visibility = View.VISIBLE
    }

    // ───────────────────────── O'CHIRISH ─────────────────────────

    private fun confirmPurge() {
        val labels = threats.map {
            val tag = if (it.level == ThreatLevel.MALWARE) "MALWARE" else "SHUBHALI"
            "[$tag] ${it.name.substringBefore("  [")}\n${it.target}" as CharSequence
        }.toTypedArray()
        // MALWARE oldindan belgilanadi, shubhalilarni foydalanuvchi o'zi tanlaydi.
        val checked = BooleanArray(threats.size) { threats[it].level == ThreatLevel.MALWARE }
        AlertDialog.Builder(this)
            .setTitle("> O'chiriladigan nishonlar")
            .setMultiChoiceItems(labels, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("O'CHIRISH") { _, _ ->
                val selected = threats.filterIndexed { i, _ -> checked[i] }
                if (selected.isNotEmpty()) startPurge(selected)
            }
            .setNegativeButton("BEKOR", null)
            .show()
    }

    private fun startPurge(queue: List<Threat>) {
        if (worker != null) return
        btnDelete.visibility = View.GONE
        btnMenu.visibility = View.GONE
        logView.setText("", TextView.BufferType.EDITABLE)
        lastProgressPct = -1
        header.text = "root@qoriqchi:~# ./purge --threats --force"
        applyTheme(Palette.GREEN)
        setStatus("[ ZARARSIZLANTIRILMOQDA... ]", Palette.GREEN, true)

        worker = thread(name = "purge") {
            try {
                console.line("[*] Zararsizlantirish moduli ishga tushdi", Palette.BRIGHT, 200)
                console.line("[*] Nishonlar: ${queue.size}", Palette.GREEN, 150)
                var removed = 0
                queue.forEachIndexed { i, t ->
                    console.progress(i, queue.size)
                    console.line()
                    console.line("[>] [${i + 1}/${queue.size}] ${t.family}", Palette.BRIGHT, 150)
                    console.line("    nishon: ${t.target}", Palette.GREEN_DIM, 80)
                    val ok = if (t.kind == ThreatKind.APP) purgeApp(t) else purgeFile(t)
                    if (ok) {
                        removed++
                        console.line("    [✓] YO'Q QILINDI", Palette.GREEN, 200)
                    } else {
                        console.line("    [✗] o'chirilmadi — qo'lda o'chiring", Palette.YELLOW, 200)
                    }
                }
                console.progress(queue.size, queue.size)

                val left = threats.size - removed
                prefs.totalRemoved = prefs.totalRemoved + removed
                prefs.openThreats = left
                console.line()
                console.line("════════════════════════════════════", Palette.GREEN, 100)
                Art.PURGED.forEach { console.line(it, Palette.GREEN, 80) }
                console.line("════════════════════════════════════", Palette.GREEN, 100)
                console.line(" o'chirildi: $removed  |  qoldi: $left", Palette.GREEN, 150)
                console.line(
                    if (left == 0) "[✓] Telefon tozalandi." else "[!] Ba'zi tahdidlar qoldi — qayta skanerlang.",
                    if (left == 0) Palette.GREEN else Palette.YELLOW, 300,
                )
                runOnUiThread { setStatus(if (left == 0) "[✓] TOZALANDI" else "[!] QISMAN TOZALANDI", Palette.GREEN, false) }
                console.line()
                for (s in 3 downTo 1) console.line("[*] Menyuga qaytilmoqda... $s", Palette.GREEN_DIM, 1000)
                runOnUiThread { finish() }
            } catch (_: CancellationException) {
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun purgeApp(t: Threat): Boolean {
        val pkg = t.target
        console.line("    [*] qoldiqlar qidirilmoqda (o'rnatuvchi APK, yozgan fayllari)...", Palette.GREEN_DIM, 100)
        val leftovers = Remover.leftovers(this, pkg, Tag.SIGNATURE in t.tags)
        console.line("    [+] qoldiq: ${leftovers.size} ta fayl", Palette.GREEN_DIM, 100)
        console.line("    [*] jarayon to'xtatilmoqda...", Palette.GREEN_DIM, 150)
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(pkg)
        if (Scanner.isAdminActive(this, pkg)) {
            console.line("    [!] ilova qurilma administratori — tizim avval uni o'chirishni so'raydi", Palette.YELLOW, 200)
        }
        if (AutoConfirm.isEnabled(this)) {
            console.line("    [*] auto-confirm: tizim oynasi avtomatik tasdiqlanadi", Palette.GREEN_DIM, 150)
        } else {
            console.line("    [!] auto-confirm o'chiq — oynada OK bosing (yoqish: REAL-TIME HIMOYA)", Palette.YELLOW, 150)
        }
        console.line("    [*] tizimga o'chirish so'rovi yuborildi...", Palette.GREEN_DIM, 250)

        val latch = CountDownLatch(1)
        uninstallLatch = latch
        AutoConfirm.arm(pkg, t.name.substringBefore("  ["))
        runOnUiThread {
            try {
                startActivityForResult(Remover.uninstallIntent(pkg), REQ_UNINSTALL)
            } catch (e: Exception) {
                latch.countDown()
            }
        }
        latch.await()
        uninstallLatch = null
        AutoConfirm.disarm()
        console.check()
        Thread.sleep(400)
        console.line("    [*] paket tekshirilmoqda...", Palette.GREEN_DIM, 150)
        if (Remover.isInstalled(this, pkg)) return false
        val cleaned = Remover.cleanLeftovers(leftovers, pkg) { console.line("    [*] $it", Palette.GREEN_DIM, 60) }
        console.line("    [+] /data/data/$pkg + kesh: tizim tomonidan o'chirildi", Palette.GREEN_DIM, 80)
        console.line("    [+] qoldiqlar tozalandi: $cleaned ta", Palette.GREEN_DIM, 100)
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_UNINSTALL) uninstallLatch?.countDown()
    }

    private fun purgeFile(t: Threat): Boolean {
        val f = File(t.target)
        if (!f.exists()) {
            console.line("    [*] fayl allaqachon yo'q", Palette.GREEN_DIM, 100)
            return true
        }
        console.line("    [*] fayl qulflandi: ${Scanner.human(f.length())}", Palette.GREEN_DIM, 150)
        return Remover.shredDelete(f) { console.line("    [*] $it", Palette.GREEN_DIM, 120) }
    }

    // ───────────────────────── TERMINAL ─────────────────────────

    private fun append(text: String, color: Int) {
        val span = SpannableString(text + "\n")
        span.setSpan(ForegroundColorSpan(color), 0, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        logView.append(span)
        val editable = logView.editableText
        if (editable != null && editable.length > MAX_LOG_CHARS) {
            val cut = editable.indexOf('\n', editable.length - MAX_LOG_CHARS)
            editable.delete(0, if (cut > 0) cut + 1 else editable.length - MAX_LOG_CHARS)
        }
        scroll.post { scroll.scrollTo(0, logView.bottom) }
    }

    private fun applyTheme(color: Int) {
        frame.background = GradientDrawable().apply {
            setColor(Palette.BG_TERM)
            setStroke((resources.displayMetrics.density * 1.5f).toInt(), color)
        }
        header.setTextColor(color)
        header.setBackgroundColor(color and 0x00FFFFFF or 0x22000000)
        progressView.setTextColor(color)
    }

    private fun setStatus(text: String, color: Int, blinking: Boolean) {
        statusLine.text = text
        statusLine.setTextColor(color)
        statusLine.setShadowLayer(14f, 0f, 0f, color)
        blink?.cancel()
        statusLine.alpha = 1f
        if (blinking) {
            blink = ObjectAnimator.ofFloat(statusLine, View.ALPHA, 1f, 0.25f).apply {
                duration = 550
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }
    }
}
