package uz.apkqoriqchi

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.widget.ImageView
import android.widget.TextView
import java.util.Date

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var statusIcon: ImageView
    private lateinit var statusTitle: TextView
    private lateinit var statusText: TextView
    private lateinit var guardTitle: TextView
    private lateinit var guardSwitch: android.widget.Switch
    private lateinit var webTitle: TextView
    private lateinit var webSwitch: android.widget.Switch
    private lateinit var guardSetup: GuardSetup
    private lateinit var breath: android.animation.ObjectAnimator
    private var introPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Ui.applySystemBarInsets(findViewById(R.id.root))
        prefs = Prefs(this)

        statusIcon = findViewById(R.id.statusIcon)
        statusTitle = findViewById(R.id.statusTitle)
        statusText = findViewById(R.id.statusText)
        findViewById<TextView>(R.id.tip).text = Explain.HOME_TIPS.random()

        findViewById<TextView>(R.id.btnScan).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
        findViewById<TextView>(R.id.btnHelp).setOnClickListener { help() }

        guardTitle = findViewById(R.id.guardTitle)
        guardSwitch = findViewById(R.id.guardSwitch)
        findViewById<android.view.View>(R.id.guardCard).setOnClickListener { guardSwitch.toggle() }
        guardSwitch.setOnCheckedChangeListener { _, on ->
            if (on == prefs.realtime) return@setOnCheckedChangeListener
            if (on) {
                Realtime.enable(this)
                guardSetup.start()
            } else {
                Realtime.disable(this)
            }
            renderGuard()
            Motion.flash(guardTitle)
        }
        webTitle = findViewById(R.id.webTitle)
        webSwitch = findViewById(R.id.webSwitch)
        findViewById<android.view.View>(R.id.webCard).setOnClickListener { webSwitch.toggle() }
        webSwitch.setOnCheckedChangeListener { _, on ->
            if (on == webOn()) return@setOnCheckedChangeListener
            if (on) {
                WebGuard.enable(this)
                if (!AutoConfirm.isEnabled(this)) askWebAccess()
            } else {
                WebGuard.disable(this)
            }
            renderWeb()
            Motion.flash(webTitle)
        }
        guardSetup = GuardSetup(
            this,
            GuardSetup.Texts(
                storageTitle = "Ruxsat kerak",
                storageMessage = "Telegram'dan kelgan fayllarni darhol tekshirishimiz uchun fayllarga ruxsat bering.\n\nKeyingi oynada \"Telefon Qo'riqchi\" yonidagi tugmani yoqing, so'ng orqaga qayting.",
                autoRemoveTitle = "Xavfli ilovalarni o'zi o'chirsinmi?",
                autoRemoveMessage = "Yoqsangiz, xavfli ilova topilishi bilan Qo'riqchi uni o'zi o'chiradi — siz hech narsa bosmaysiz.\n\n" +
                    "Keyingi oynada \"Telefon Qo'riqchi: avtomatik o'chirish\" ni toping va yoqing, so'ng orqaga qayting.\n\n" +
                    "Agar tugma bosilmasa, buni oilangizdagi telefonni yaxshi biladigan odamdan so'rang.",
                autostartTitle = "Yana bitta sozlama",
                autostartMessage = "Himoya doim ishlab turishi uchun keyingi oynada \"Telefon Qo'riqchi\" yonidagi tugmani yoqing.",
                popupTitle = "Oxirgi sozlama",
                popupMessage = "Xavfli ilovani o'zi o'chira olishi uchun keyingi oynada \"Fonda ishlayotganda qalqib chiquvchi oynalarni ko'rsatish\" ni yoqing.",
                ok = "Sozlash",
                later = "Keyinroq",
            ),
        )

        Motion.pressable(findViewById(R.id.btnScan), findViewById(R.id.btnHelp))
        Motion.pressable(findViewById(R.id.guardCard), findViewById(R.id.webCard), scale = 0.98f)
        breath = Motion.breathe(statusIcon)
        if (savedInstanceState == null) {
            val menu = Motion.items(findViewById(R.id.root))
            Motion.hide(menu, dyDp = 28f)
            introPlaying = Motion.enabled()
            Motion.intro(
                this,
                IntroView.Style(
                    bg = getColor(R.color.bg),
                    accent = getColor(R.color.primary),
                    mark = getColor(R.color.card),
                    text = getColor(R.color.text),
                    text2 = getColor(R.color.text2),
                    title = getString(R.string.app_name),
                    subtitle = "Telefoningizni xavfli ilovalardan himoya qiladi",
                    terminal = false,
                ),
            ) {
                Motion.stagger(menu)
                if (introPlaying) {
                    introPlaying = false
                    guardSetup.onResume()
                }
            }
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
        if (!Motion.enabled()) return
        if (breath.isStarted) breath.resume() else breath.start()
    }

    override fun onPause() {
        breath.pause()
        super.onPause()
    }

    private fun renderGuard() {
        val on = prefs.realtime
        guardSwitch.isChecked = on
        guardTitle.text = if (on) "Doimiy himoya yoqilgan" else "Doimiy himoya o'chiq"
        guardTitle.setTextColor(getColor(if (on) R.color.safe else R.color.text))
    }

    /** Himoya haqiqatan ishlayaptimi: yoqilgan va maxsus imkoniyatlar ruxsati berilgan. */
    private fun webOn() = prefs.webGuard && AutoConfirm.isEnabled(this)

    private fun renderWeb() {
        val on = webOn()
        webSwitch.isChecked = on
        webTitle.text = when {
            on -> "Saytlardan himoya yoqilgan"
            prefs.webGuard -> "Yoqish uchun ruxsat kerak"
            else -> "Saytlardan himoya o'chiq"
        }
        webTitle.setTextColor(getColor(if (on) R.color.safe else if (prefs.webGuard) R.color.warn else R.color.text))
    }

    private fun askWebAccess() {
        AlertDialog.Builder(this)
            .setTitle("Bitta ruxsat kerak")
            .setMessage(
                "Firibgar saytlarni tanib olishimiz uchun Qo'riqchi brauzerda ochilgan sayt manzilini ko'rishi kerak. " +
                    "Xabarlaringiz va parollaringiz o'qilmaydi.\n\n" +
                    "Keyingi oynada \"Telefon Qo'riqchi: avtomatik o'chirish\" ni toping va yoqing, so'ng orqaga qayting.",
            )
            .setPositiveButton("Sozlash") { _, _ ->
                try {
                    startActivity(AutoConfirm.settingsIntent())
                } catch (e: Exception) {
                }
            }
            .setNegativeButton("Keyinroq", null)
            .show()
    }

    private fun renderStatus() {
        val last = prefs.lastScan
        val open = prefs.openThreats
        when {
            open > 0 -> {
                statusIcon.setImageResource(R.drawable.ic_shield_warn)
                statusTitle.text = "$open ta muammo hal qilinmagan"
                statusText.text = "Telefonni qayta tekshirib, ularni o'chirib qo'ying."
            }
            last == 0L -> {
                statusIcon.setImageResource(R.drawable.ic_shield_scan)
                statusTitle.text = "Telefon hali tekshirilmagan"
                statusText.text = "Pastdagi tugmani bosib, birinchi tekshiruvni boshlang."
            }
            else -> {
                statusIcon.setImageResource(R.drawable.ic_shield_ok)
                statusTitle.text = "Telefoningiz himoyada"
                statusText.text = "Oxirgi tekshiruv: ${lastScanText(last)}" +
                    (if (prefs.totalRemoved > 0) "\nJami o'chirilgan xavfli narsalar: ${prefs.totalRemoved} ta" else "") +
                    if (prefs.sitesBlocked > 0) "\nYopilgan xavfli saytlar: ${prefs.sitesBlocked} ta" else ""
            }
        }
    }

    private fun lastScanText(time: Long): String = when {
        DateUtils.isToday(time) -> "bugun, " + DateFormat.format("HH:mm", Date(time))
        DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> "kecha, " + DateFormat.format("HH:mm", Date(time))
        else -> DateFormat.format("dd.MM.yyyy", Date(time)).toString()
    }

    private fun help() {
        AlertDialog.Builder(this)
            .setTitle("Telefon Qo'riqchi")
            .setMessage(
                "Bu dastur telefoningizdagi ilovalar va yuklab olingan fayllarni tekshiradi.\n\n" +
                    "U quyidagilarni topadi:\n" +
                    "• SMS-kodlar va bank ma'lumotlarini o'g'irlaydigan ilovalar\n" +
                    "• Telegram, Payme, Click kabi ilovalarning soxta nusxalari\n" +
                    "• \"Rasm\" yoki \"hujjat\" deb yuborilgan aldamchi fayllar\n" +
                    "• Brauzerda ochilgan firibgar saytlar (soxta Payme, Click, bank saytlari)\n\n" +
                    "Yangi ilova o'rnatilishi bilan u sinchiklab tekshiriladi. Xavfli bo'lsa — o'zi o'chiriladi, undan qolgan fayllar ham tozalanadi.\n\n" +
                    "Haftada bir marta tekshirib turishni maslahat beramiz."
            )
            .setPositiveButton("Tushunarli", null)
            .show()
    }
}
