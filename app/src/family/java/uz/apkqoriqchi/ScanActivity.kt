package uz.apkqoriqchi

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.concurrent.CancellationException
import kotlin.concurrent.thread

class ScanActivity : Activity() {

    companion object {
        private const val REQ_UNINSTALL = 21
    }

    private lateinit var scanningView: View
    private lateinit var scanIcon: ImageView
    private lateinit var scanStage: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var percent: TextView
    private lateinit var scanItem: TextView
    private lateinit var resultView: ScrollView
    private lateinit var resultIcon: ImageView
    private lateinit var resultTitle: TextView
    private lateinit var resultText: TextView
    private lateinit var list: LinearLayout
    private lateinit var tips: LinearLayout
    private lateinit var btnRemoveAll: TextView
    private lateinit var btnHome: TextView

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private var pulse: ObjectAnimator? = null
    private var started = false
    private var waitingForStorageSettings = false

    @Volatile private var cancelled = false
    @Volatile private var stageIndex = 0
    @Volatile private var stageCount = 3
    private var lastPct = -1
    private var lastItemAt = 0L

    private class Card(val actions: LinearLayout, val state: TextView)

    private val cards = LinkedHashMap<Threat, Card>()
    private val resolved = mutableSetOf<Threat>()
    private val pending = ArrayDeque<Threat>()
    private var current: Threat? = null
    private var removedCount = 0

    private val console = object : Console {
        override fun line(text: String, color: Int, pause: Long) = check()

        override fun progress(done: Int, total: Int) {
            val frac = if (total == 0) 1f else done.toFloat() / total
            val pct = (((stageIndex - 1).coerceAtLeast(0) + frac) * 100 / stageCount).toInt().coerceIn(0, 100)
            if (pct == lastPct) return
            lastPct = pct
            runOnUiThread {
                progressBar.progress = pct
                percent.text = "$pct%"
            }
        }

        override fun check() {
            if (cancelled) throw CancellationException()
        }

        override fun stage(title: String) {
            stageIndex++
            val text = "$stageIndex-qadam ($stageCount dan): $title"
            runOnUiThread { scanStage.text = text }
        }

        override fun item(name: String) {
            val now = SystemClock.uptimeMillis()
            if (now - lastItemAt < 70) return
            lastItemAt = now
            runOnUiThread { scanItem.text = name }
            Thread.sleep(20)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        Ui.applySystemBarInsets(findViewById(R.id.root))
        prefs = Prefs(this)

        scanningView = findViewById(R.id.scanningView)
        scanIcon = findViewById(R.id.scanIcon)
        scanStage = findViewById(R.id.scanStage)
        progressBar = findViewById(R.id.progressBar)
        percent = findViewById(R.id.percent)
        scanItem = findViewById(R.id.scanItem)
        resultView = findViewById(R.id.resultView)
        resultIcon = findViewById(R.id.resultIcon)
        resultTitle = findViewById(R.id.resultTitle)
        resultText = findViewById(R.id.resultText)
        list = findViewById(R.id.list)
        tips = findViewById(R.id.tips)
        btnRemoveAll = findViewById(R.id.btnRemoveAll)
        btnHome = findViewById(R.id.btnHome)

        findViewById<View>(R.id.btnCancel).setOnClickListener { finish() }
        btnHome.setOnClickListener { finish() }
        btnRemoveAll.setOnClickListener { confirmRemoveAll() }

        if (Storage.has(this)) startScan(true) else askStorage()
    }

    override fun onResume() {
        super.onResume()
        if (waitingForStorageSettings) {
            waitingForStorageSettings = false
            startScan(Storage.has(this))
        }
    }

    override fun onDestroy() {
        cancelled = true
        pulse?.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ───────────────────────── Ruxsat ─────────────────────────

    private fun askStorage() {
        if (!Storage.needsSettingsScreen) {
            requestPermissions(Storage.LEGACY_PERMISSIONS, Storage.REQUEST_CODE)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Ruxsat kerak")
            .setMessage(
                "Yuklab olingan fayllarni (masalan, Telegram'dan kelganlarini) ham tekshirishimiz uchun fayllarga ruxsat bering.\n\n" +
                    "Keyingi oynada \"Telefon Qo'riqchi\" yonidagi tugmani yoqing, so'ng orqaga qayting."
            )
            .setCancelable(false)
            .setPositiveButton("Ruxsat berish") { _, _ ->
                waitingForStorageSettings = true
                val opened = Storage.settingsIntents(this).any {
                    try {
                        startActivity(it)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                if (!opened) startScan(false)
            }
            .setNegativeButton("Hozir emas") { _, _ -> startScan(false) }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Storage.REQUEST_CODE) startScan(Storage.has(this))
    }

    // ───────────────────────── Tekshirish ─────────────────────────

    private fun startScan(withFiles: Boolean) {
        if (started) return
        started = true
        stageCount = if (withFiles) 3 else 2
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            scanIcon,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f),
        ).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        thread(name = "scanner") {
            try {
                val scanner = Scanner(this, console)
                val found = mutableListOf<Threat>()
                found += scanner.scanApps()
                if (withFiles) found += scanner.scanFiles()
                val issues = scanner.deviceChecks()
                console.progress(1, 1)
                Thread.sleep(500)
                val trusted = prefs.trusted
                val shown = found.filter { it.target !in trusted }.sortedBy { it.level }
                runOnUiThread { showResults(shown, issues, scanner.appsScanned, scanner.filesScanned) }
            } catch (_: CancellationException) {
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun showResults(threats: List<Threat>, issues: List<Scanner.Issue>, apps: Int, files: Int) {
        pulse?.cancel()
        prefs.lastScan = System.currentTimeMillis()
        prefs.openThreats = threats.size
        scanningView.visibility = View.GONE
        resultView.visibility = View.VISIBLE

        if (threats.isEmpty()) {
            resultIcon.setImageResource(R.drawable.ic_shield_ok)
            resultTitle.text = "Hammasi joyida!"
            resultText.text = "Telefoningizda xavfli narsa topilmadi.\n" +
                "Tekshirildi: $apps ta ilova" + (if (files > 0) " va $files ta fayl" else "")
        } else {
            val dangerous = threats.count { it.level == ThreatLevel.MALWARE }
            resultIcon.setImageResource(R.drawable.ic_shield_warn)
            resultTitle.text = if (dangerous > 0) "${threats.size} ta xavfli narsa topildi" else "${threats.size} ta shubhali narsa topildi"
            resultText.text = "Xavotir olmang — ularni hozir o'chirib qo'yishingiz mumkin."
            threats.forEach { addCard(it) }
            updateRemoveAll()
        }
        issues.mapNotNull { Explain.tip(it) }.takeIf { it.isNotEmpty() }?.let { addTips(it) }
    }

    // ───────────────────────── Kartochkalar ─────────────────────────

    private fun addCard(t: Threat) {
        val danger = t.level == ThreatLevel.MALWARE
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card)
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text(t.name.substringBefore("  ["), 18f, R.color.text, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(chip(Explain.levelLabel(t), if (danger) R.color.danger else R.color.warn, if (danger) R.color.danger_bg else R.color.warn_bg))
        card.addView(header)
        card.addView(text(Explain.kindLabel(t), 14f, R.color.text2).apply { setPadding(0, dp(2), 0, 0) })

        Explain.reasons(t).forEach { reason ->
            card.addView(text("•  $reason", 16f, R.color.text).apply { setPadding(0, dp(10), 0, 0) })
        }
        card.addView(text(Explain.advice(t), 14f, R.color.text2).apply { setPadding(0, dp(10), 0, 0) })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        val remove = text("O'chirish", 16f, android.R.color.white, bold = true).apply {
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.btn_danger)
            setOnClickListener { removeOne(t) }
        }
        actions.addView(remove, LinearLayout.LayoutParams(0, dp(50), 1f))
        val keep = text("Qoldirish", 16f, R.color.text2).apply {
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.btn_text)
            setPadding(dp(20), 0, dp(20), 0)
            setOnClickListener { confirmKeep(t) }
        }
        actions.addView(keep, LinearLayout.LayoutParams(-2, dp(50)))
        card.addView(actions)

        val state = text("", 16f, R.color.safe, bold = true).apply {
            setPadding(0, dp(14), 0, 0)
            visibility = View.GONE
        }
        card.addView(state)

        list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        cards[t] = Card(actions, state)
    }

    private fun addTips(items: List<Explain.Tip>) {
        tips.addView(text("Maslahatlar", 18f, R.color.text, bold = true).apply { setPadding(dp(4), dp(24), 0, 0) })
        for (tip in items) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.card)
                setPadding(dp(18), dp(16), dp(18), dp(12))
            }
            card.addView(text(tip.title, 16f, R.color.text, bold = true))
            card.addView(text(tip.text, 15f, R.color.text2).apply { setPadding(0, dp(6), 0, 0) })
            tip.settingsAction?.let { action ->
                card.addView(text("Sozlamalarni ochish", 15f, R.color.primary, bold = true).apply {
                    setBackgroundResource(R.drawable.btn_text)
                    setPadding(0, dp(10), dp(10), dp(4))
                    setOnClickListener {
                        try {
                            startActivity(Intent(action))
                        } catch (e: Exception) {
                            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                        }
                    }
                })
            }
            tips.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
    }

    // ───────────────────────── O'chirish ─────────────────────────

    private fun removeOne(t: Threat) {
        if (current != null) return
        if (t.kind == ThreatKind.FILE) {
            AlertDialog.Builder(this)
                .setTitle("Faylni o'chirasizmi?")
                .setMessage("\"${t.target.substringAfterLast('/')}\" butunlay o'chiriladi.")
                .setPositiveButton("O'chirish") { _, _ -> enqueue(listOf(t)) }
                .setNegativeButton("Bekor qilish", null)
                .show()
        } else {
            enqueue(listOf(t))
        }
    }

    private fun confirmRemoveAll() {
        if (current != null) return
        val left = cards.keys.filter { it !in resolved }
        val apps = left.count { it.kind == ThreatKind.APP }
        val files = left.size - apps
        val what = listOfNotNull(
            if (apps > 0) "$apps ta ilova" else null,
            if (files > 0) "$files ta fayl" else null,
        ).joinToString(" va ")
        val msg = "$what o'chiriladi." +
            if (apps > 0) "\n\nHar bir ilova uchun telefon \"O'chirilsinmi?\" deb so'raydi — \"OK\" tugmasini bosing." else ""
        AlertDialog.Builder(this)
            .setTitle("Hammasini o'chirish")
            .setMessage(msg)
            .setPositiveButton("O'chirish") { _, _ -> enqueue(left) }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun enqueue(items: List<Threat>) {
        pending.clear()
        pending.addAll(items)
        processNext()
    }

    private fun processNext() {
        val t = pending.removeFirstOrNull()
        if (t == null) {
            current = null
            checkAllDone()
            return
        }
        val card = cards[t] ?: return processNext()
        current = t
        card.actions.visibility = View.GONE
        card.state.visibility = View.VISIBLE
        card.state.setTextColor(getColor(R.color.text2))
        card.state.text = "O'chirilmoqda..."

        if (t.kind == ThreatKind.FILE) {
            thread {
                val ok = Remover.shredDelete(File(t.target))
                runOnUiThread { finishItem(t, ok) }
            }
            return
        }
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(t.target)
        try {
            startActivityForResult(Remover.uninstallIntent(t.target), REQ_UNINSTALL)
        } catch (e: Exception) {
            finishItem(t, false)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_UNINSTALL) return
        val t = current ?: return
        handler.postDelayed({ finishItem(t, !Remover.isInstalled(this, t.target)) }, 400)
    }

    private fun finishItem(t: Threat, ok: Boolean) {
        val card = cards[t] ?: return
        if (ok) {
            removedCount++
            prefs.totalRemoved = prefs.totalRemoved + 1
            resolved += t
            card.state.setTextColor(getColor(R.color.safe))
            card.state.text = "✓  O'chirildi"
        } else {
            card.actions.visibility = View.VISIBLE
            card.state.setTextColor(getColor(R.color.danger))
            card.state.text = if (t.kind == ThreatKind.APP && Scanner.isAdminActive(this, t.target)) {
                "Bu ilova o'zini o'chirishdan himoyalagan. Sozlamalar → Xavfsizlik → Qurilma administratorlari bo'limida uni o'chirib qo'ying, so'ng qayta urinib ko'ring."
            } else {
                "O'chirilmadi. Qayta urinib ko'ring."
            }
        }
        current = null
        processNext()
    }

    private fun confirmKeep(t: Threat) {
        if (current != null) return
        val msg = if (t.level == ThreatLevel.MALWARE) {
            "Bu narsa xavfli deb topildi. Biz uni o'chirishni qat'iy maslahat beramiz.\n\nBaribir qoldirilsinmi? Keyingi tekshiruvlarda u ko'rsatilmaydi."
        } else {
            "Agar buni o'zingiz o'rnatgan bo'lsangiz va unga ishonsangiz, qoldiring. Keyingi tekshiruvlarda u ko'rsatilmaydi."
        }
        AlertDialog.Builder(this)
            .setTitle("Qoldirilsinmi?")
            .setMessage(msg)
            .setPositiveButton("Qoldirish") { _, _ ->
                prefs.trusted = prefs.trusted + t.target
                resolved += t
                cards[t]?.let {
                    it.actions.visibility = View.GONE
                    it.state.visibility = View.VISIBLE
                    it.state.setTextColor(getColor(R.color.text2))
                    it.state.text = "Qoldirildi"
                }
                checkAllDone()
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun updateRemoveAll() {
        val left = cards.keys.count { it !in resolved }
        btnRemoveAll.text = "Hammasini o'chirish ($left)"
        btnRemoveAll.visibility = if (left > 1) View.VISIBLE else View.GONE
    }

    private fun checkAllDone() {
        val left = cards.keys.count { it !in resolved }
        prefs.openThreats = left
        updateRemoveAll()
        if (left == 0 && cards.isNotEmpty()) showFinished()
    }

    private fun showFinished() {
        list.removeAllViews()
        btnRemoveAll.visibility = View.GONE
        resultIcon.setImageResource(R.drawable.ic_shield_ok)
        if (removedCount > 0) {
            resultTitle.text = "Tayyor!"
            resultText.text = "Telefoningiz tozalandi.\n$removedCount ta xavfli narsa o'chirildi."
        } else {
            resultTitle.text = "Tekshiruv tugadi"
            resultText.text = "Siz tanlagan narsalar qoldirildi."
        }
        resultView.scrollTo(0, 0)
        for (s in 3 downTo 1) {
            handler.postDelayed({ btnHome.text = "Bosh sahifaga qaytish ($s)" }, (3 - s) * 1000L)
        }
        handler.postDelayed({ finish() }, 3000)
    }

    // ───────────────────────── Yordamchi ─────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(value: String, sizeSp: Float, colorRes: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = sizeSp
        setTextColor(getColor(colorRes))
        setLineSpacing(0f, 1.15f)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun chip(value: String, fgRes: Int, bgRes: Int) = text(value, 13f, fgRes, bold = true).apply {
        setPadding(dp(12), dp(4), dp(12), dp(4))
        background = GradientDrawable().apply {
            setColor(getColor(bgRes))
            cornerRadius = dp(20).toFloat()
        }
    }
}
