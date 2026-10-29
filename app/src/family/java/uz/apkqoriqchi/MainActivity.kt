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
    private lateinit var guardSetup: GuardSetup

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
        }
        guardSetup = GuardSetup(
            this,
            GuardSetup.Texts(
                storageTitle = "Ruxsat kerak",
                storageMessage = "Telegram'dan kelgan fayllarni darhol tekshirishimiz uchun fayllarga ruxsat bering.\n\nKeyingi oynada \"Telefon Qo'riqchi\" yonidagi tugmani yoqing, so'ng orqaga qayting.",
                autostartTitle = "Yana bitta sozlama",
                autostartMessage = "Himoya doim ishlab turishi uchun keyingi oynada \"Telefon Qo'riqchi\" yonidagi tugmani yoqing.",
                ok = "Sozlash",
                later = "Keyinroq",
            ),
        )
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        renderGuard()
        Realtime.resume(this)
        guardSetup.onResume()
    }

    private fun renderGuard() {
        val on = prefs.realtime
        guardSwitch.isChecked = on
        guardTitle.text = if (on) "Doimiy himoya yoqilgan" else "Doimiy himoya o'chiq"
        guardTitle.setTextColor(getColor(if (on) R.color.safe else R.color.text))
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
                    if (prefs.totalRemoved > 0) "\nJami o'chirilgan xavfli narsalar: ${prefs.totalRemoved} ta" else ""
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
                    "• \"Rasm\" yoki \"hujjat\" deb yuborilgan aldamchi fayllar\n\n" +
                    "Xavfli narsa topilsa, uni bitta tugma bilan o'chirasiz.\n\n" +
                    "Haftada bir marta tekshirib turishni maslahat beramiz."
            )
            .setPositiveButton("Tushunarli", null)
            .show()
    }
}
