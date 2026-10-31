package uz.apkqoriqchi

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.widget.TextView
import java.util.Date

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var cursor: TextView
    private lateinit var status: TextView
    private lateinit var prefs: Prefs
    private lateinit var btnGuard: TextView
    private lateinit var btnWeb: TextView
    private lateinit var guardSetup: GuardSetup
    private var cursorOn = true
    private var introPlaying = false

    private val blinkCursor = object : Runnable {
        override fun run() {
            cursorOn = !cursorOn
            cursor.text = "root@qoriqchi:~# " + if (cursorOn) "█" else " "
            handler.postDelayed(this, 530)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Ui.applySystemBarInsets(findViewById(R.id.root))
        prefs = Prefs(this)

        cursor = findViewById(R.id.cursor)
        status = findViewById(R.id.status)
        findViewById<TextView>(R.id.prompt).text =
            "root@${Build.MODEL.lowercase().replace(' ', '-')}:~# ./qoriqchi --menu"

        findViewById<TextView>(R.id.btnFull).setOnClickListener { scan(ScanActivity.MODE_FULL) }
        findViewById<TextView>(R.id.btnApps).setOnClickListener { scan(ScanActivity.MODE_APPS) }
        findViewById<TextView>(R.id.btnFiles).setOnClickListener { scan(ScanActivity.MODE_FILES) }
        findViewById<TextView>(R.id.btnAbout).setOnClickListener { about() }
        findViewById<TextView>(R.id.btnExit).setOnClickListener { finish() }

        btnGuard = findViewById(R.id.btnGuard)
        btnGuard.setOnClickListener { toggleGuard() }
        btnWeb = findViewById(R.id.btnWeb)
        btnWeb.setOnClickListener { toggleWeb() }
        guardSetup = GuardSetup(
            this,
            GuardSetup.Texts(
                storageTitle = "> fs.watch: ruxsat kerak",
                storageMessage = "Telegram/WhatsApp/Download papkalariga tushgan APK'larni real vaqtda ushlash uchun \"Barcha fayllarga ruxsat\" kerak.\n\nRuxsatsiz faqat yangi o'rnatilgan ilovalar tekshiriladi.",
                autoRemoveTitle = "> auto-purge: tugmasiz o'chirish",
                autoRemoveMessage = "Android begona ilovani faqat \"O'chirilsinmi? → OK\" orqali o'chiradi. Avto-tasdiq yoqilsa, OK'ni Qo'riqchi o'zi bosadi: virus topildi → o'chirildi → qoldiqlari (o'rnatuvchi APK, yozgan fayllari) shred.\n\n" +
                    "Keyingi oynada: Yuklab olingan ilovalar → \"APK Qo'riqchi: avto-o'chirish\" → YOQISH.\n\n" +
                    "Tugma kulrang bo'lsa: Ilova haqida → ⋮ → \"Cheklangan sozlamalarga ruxsat\".",
                autostartTitle = "> MIUI: avtozapusk",
                autostartMessage = "MIUI fon xizmatlarini o'ldiradi. Himoya doim ishlashi uchun:\n\n1) Avtozapusk → APK Qo'riqchi → YOQISH\n2) Batareya → Cheklovsiz",
                popupTitle = "> MIUI: fondan oyna ochish",
                popupMessage = "MIUI fondagi ilovaga oyna ochishni taqiqlaydi — auto-purge ishlamay qoladi.\n\nKeyingi oynada: \"Fonda ishlayotganda qalqib chiquvchi oynalarni ko'rsatish\" → RUXSAT.",
                ok = "SOZLASH",
                later = "KEYINROQ",
            ),
        )

        Motion.pressable(
            findViewById(R.id.btnFull), findViewById(R.id.btnApps), findViewById(R.id.btnFiles),
            btnGuard, btnWeb, findViewById(R.id.btnAbout), findViewById(R.id.btnExit),
            scale = 0.97f,
        )
        val subtitle = findViewById<TextView>(R.id.subtitle)
        val tagline = "> anti-malware terminal v1.0 // Android himoyasi"
        if (savedInstanceState == null) {
            val menu = Motion.items(findViewById(R.id.root))
            Motion.hide(menu, dxDp = -24f)
            introPlaying = Motion.enabled()
            Motion.intro(
                this,
                IntroView.Style(
                    bg = getColor(R.color.bg),
                    accent = getColor(R.color.green),
                    mark = Palette.BRIGHT,
                    text = getColor(R.color.green),
                    text2 = getColor(R.color.green_dim),
                    title = "QO'RIQCHI",
                    subtitle = "> himoya moduli yuklandi",
                    terminal = true,
                ),
            ) {
                Motion.stagger(menu, step = 45)
                typewrite(subtitle, tagline)
                if (introPlaying) {
                    introPlaying = false
                    guardSetup.onResume()
                }
            }
        } else {
            subtitle.text = tagline
        }
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        renderGuard()
        renderWeb()
        Realtime.resume(this)
        WebGuard.resume(this)
        // Sozlash oynalari intro ustiga chiqmasin — ular intro tugagach ochiladi
        if (!introPlaying) guardSetup.onResume()
        handler.post(blinkCursor)
    }

    private fun renderGuard() {
        val on = prefs.realtime
        btnGuard.text = "[4] > REAL-TIME HIMOYA: " + if (on) "[ON]" else "[OFF]"
        btnGuard.setTextColor(if (on) Palette.GREEN else Palette.GRAY)
    }

    private fun toggleGuard() {
        if (prefs.realtime) {
            Realtime.disable(this)
        } else {
            Realtime.enable(this)
            guardSetup.start()
        }
        renderGuard()
        Motion.flash(btnGuard)
    }

    private fun renderWeb() {
        val access = AutoConfirm.isEnabled(this)
        btnWeb.text = "[5] > WEB HIMOYA: " + when {
            !prefs.webGuard -> "[OFF]"
            access -> "[ON]"
            else -> "[!] ACCESSIBILITY KERAK"
        }
        btnWeb.setTextColor(
            when {
                !prefs.webGuard -> Palette.GRAY
                access -> Palette.GREEN
                else -> Palette.YELLOW
            },
        )
    }

    private fun toggleWeb() {
        val access = AutoConfirm.isEnabled(this)
        if (prefs.webGuard && access) {
            WebGuard.disable(this)
        } else {
            WebGuard.enable(this)
            if (!access) {
                AlertDialog.Builder(this)
                    .setTitle("> web-guard: accessibility kerak")
                    .setMessage(
                        "Brauzerdagi saytni tekshirish uchun Qo'riqchi manzil satrini o'qiydi (Chrome, Yandex, Samsung, Firefox, Opera, Edge, Telegram'dan ochilgan Chrome tab).\n\n" +
                            "Keyingi oynada: Yuklab olingan ilovalar → \"APK Qo'riqchi: avto-o'chirish\" → YOQISH.\n\n" +
                            "Baza (URLhaus + phishing ro'yxatlari) kuniga bir marta yuklanadi. Siz kirgan saytlar hech qayerga yuborilmaydi.",
                    )
                    .setPositiveButton("SOZLASH") { _, _ ->
                        try {
                            startActivity(AutoConfirm.settingsIntent())
                        } catch (e: Exception) {
                        }
                    }
                    .setNegativeButton("KEYINROQ", null)
                    .show()
            }
        }
        renderWeb()
        renderStatus()
        Motion.flash(btnWeb)
    }

    override fun onPause() {
        handler.removeCallbacks(blinkCursor)
        super.onPause()
    }

    private fun renderStatus() {
        val last = prefs.lastScan
        val open = prefs.openThreats
        val when_ = if (last == 0L) "hali skanerlanmagan" else DateFormat.format("dd.MM.yyyy HH:mm", Date(last)).toString()
        status.text = buildString {
            append(if (open > 0) "[!] XAVF: $open ta tahdid qolgan\n" else "[✓] HIMOYA FAOL\n")
            append("    oxirgi skan : $when_\n")
            append("    yo'q qilindi: ${prefs.totalRemoved} ta tahdid")
            if (prefs.webGuard) {
                val (domains, updated) = WebDb.stats(this@MainActivity)
                append("\n    web bloklar : ${prefs.sitesBlocked} ta sayt")
                append("\n    web-baza    : ")
                append(if (updated == 0L) "yuklanmoqda..." else "$domains domen · " + DateFormat.format("dd.MM HH:mm", Date(updated)))
            }
        }
        status.setTextColor(if (open > 0) Palette.RED else Palette.GREEN)
        status.setBackgroundResource(if (open > 0) R.drawable.btn_danger else R.drawable.btn_terminal)
    }

    private fun scan(mode: Int) {
        startActivity(Intent(this, ScanActivity::class.java).putExtra(ScanActivity.EXTRA_MODE, mode))
    }

    private fun typewrite(view: TextView, text: String) {
        for (i in 1..text.length) {
            handler.postDelayed({ view.text = text.substring(0, i) }, i * 28L)
        }
    }

    private fun about() {
        AlertDialog.Builder(this)
            .setTitle("> APK Qo'riqchi v1.0")
            .setMessage(
                "Telefondagi zararli ilova va APK fayllarni evristik tahlil qiladi:\n\n" +
                    "• SMS / bank troyanlari (SMS + Accessibility + yashirin ikonka)\n" +
                    "• Soxta Telegram, Payme, Click, Play Market va boshq.\n" +
                    "• \"Foto.apk\", \"hujjat.pdf.apk\" kabi aldamchi fayllar\n" +
                    "• Rasmiy do'kondan o'rnatilmagan ilovalar ruxsatlari\n" +
                    "• EICAR test-virus imzosi\n" +
                    "• Deep: soxta ZIP yozuvlari, shifrlangan yuk, packer, Telegram-bot\n" +
                    "• Real-time --strict: yangi ilova → tekshiruv → auto-purge + qoldiqlar\n" +
                    "• Web: brauzerda phishing / virusli sayt → yopiladi (URLhaus + phishing bazasi, soxta Payme/Click/bank domenlari, punycode, IP, .apk)\n" +
                    "• ADB, root, ekran qulfi, xavfsizlik yangilanishi\n\n" +
                    "Ball ≥ ${Rules.MALWARE_SCORE} → MALWARE, ≥ ${Rules.SUSPICIOUS_SCORE} → SHUBHALI (real-time: ≥ ${Rules.STRICT_SUSPICIOUS_SCORE}).\n\n" +
                    "Maslahat: Telegram'da kelgan APK fayllarni hech qachon o'rnatmang."
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
