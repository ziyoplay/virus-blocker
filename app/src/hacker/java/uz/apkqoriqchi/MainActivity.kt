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
    private lateinit var guardSetup: GuardSetup
    private var cursorOn = true

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
        typewrite(findViewById(R.id.subtitle), "> anti-malware terminal v1.0 // Android himoyasi")

        findViewById<TextView>(R.id.btnFull).setOnClickListener { scan(ScanActivity.MODE_FULL) }
        findViewById<TextView>(R.id.btnApps).setOnClickListener { scan(ScanActivity.MODE_APPS) }
        findViewById<TextView>(R.id.btnFiles).setOnClickListener { scan(ScanActivity.MODE_FILES) }
        findViewById<TextView>(R.id.btnAbout).setOnClickListener { about() }
        findViewById<TextView>(R.id.btnExit).setOnClickListener { finish() }

        btnGuard = findViewById(R.id.btnGuard)
        btnGuard.setOnClickListener { toggleGuard() }
        guardSetup = GuardSetup(
            this,
            GuardSetup.Texts(
                storageTitle = "> fs.watch: ruxsat kerak",
                storageMessage = "Telegram/WhatsApp/Download papkalariga tushgan APK'larni real vaqtda ushlash uchun \"Barcha fayllarga ruxsat\" kerak.\n\nRuxsatsiz faqat yangi o'rnatilgan ilovalar tekshiriladi.",
                autostartTitle = "> MIUI: avtozapusk",
                autostartMessage = "MIUI fon xizmatlarini o'ldiradi. Himoya doim ishlashi uchun:\n\n1) Avtozapusk → APK Qo'riqchi → YOQISH\n2) Batareya → Cheklovsiz",
                ok = "SOZLASH",
                later = "KEYINROQ",
            ),
        )
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        renderGuard()
        Realtime.resume(this)
        guardSetup.onResume()
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
                    "• ADB, root, ekran qulfi, xavfsizlik yangilanishi\n\n" +
                    "Ball ≥ ${Rules.MALWARE_SCORE} → MALWARE, ≥ ${Rules.SUSPICIOUS_SCORE} → SHUBHALI.\n\n" +
                    "Maslahat: Telegram'da kelgan APK fayllarni hech qachon o'rnatmang."
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
