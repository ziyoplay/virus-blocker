package uz.apkqoriqchi

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Yangi o'rnatilgan ilova oynasi: tekshiruv logi, xulosa (virus / virus emas), risklar va
 * "Qoldirish / O'chirish". Tekshiruvni [GuardService] bajaradi, bu oyna uning logini ko'rsatadi.
 * Messenjerdan kelgan aniq virus bo'lsa, log tugashi bilan o'zi o'chiradi.
 */
class CheckActivity : Activity() {

    companion object {
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_LINES = "lines"
        private const val EXTRA_COLORS = "colors"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_VERDICT = "verdict"
        private const val EXTRA_LEVEL = "level"
        private const val EXTRA_KNOWN = "known"
        private const val EXTRA_AUTO = "auto"
        private const val EXTRA_NOTIFICATION = "notification"
        private const val LINE_MS = 35L

        fun intent(ctx: Context, pkg: String, label: String, t: Threat?, log: LogConsole, notificationId: Int, auto: Boolean): Intent =
            Intent(ctx, CheckActivity::class.java)
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_LINES, log.texts.toTypedArray())
                .putExtra(EXTRA_COLORS, log.colors.toIntArray())
                .putExtra(EXTRA_TITLE, Voice.checkTitle(label, t))
                .putExtra(EXTRA_VERDICT, Voice.verdictText(label, t))
                .putExtra(EXTRA_LEVEL, t?.level?.name)
                .putExtra(EXTRA_KNOWN, t != null && Tag.SIGNATURE in t.tags)
                .putExtra(EXTRA_AUTO, auto)
                .putExtra(EXTRA_NOTIFICATION, notificationId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var pkg: String
    private lateinit var label: String
    private var threat = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra(EXTRA_PKG) ?: return finish()
        label = intent.getStringExtra(EXTRA_LABEL) ?: pkg
        threat = intent.getStringExtra(EXTRA_LEVEL) != null
        val danger = intent.getStringExtra(EXTRA_LEVEL) == ThreatLevel.MALWARE.name
        val lines = intent.getStringArrayExtra(EXTRA_LINES).orEmpty()
        val colors = intent.getIntArrayExtra(EXTRA_COLORS) ?: IntArray(lines.size) { Palette.GREEN }

        val pad = dp(20)
        val verdict = TextView(this).apply {
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        }
        val log = TextView(this).apply {
            textSize = 12f
            if (Voice.MONO_LOG) typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(log) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(8), pad, 0)
            addView(verdict)
            addView(scroll, LinearLayout.LayoutParams(-1, dp(300)))
        }

        val dialog = AlertDialog.Builder(this, Voice.DIALOG_THEME)
            .setTitle(Voice.checkingTitle(label))
            .setView(box)
            .setPositiveButton(if (threat) Voice.REMOVE else Voice.CLOSE, null)
            .setNegativeButton(if (threat) Voice.KEEP else Voice.REMOVE, null)
            .setOnCancelListener { finish() }
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        val negative = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
        positive.isEnabled = false
        negative.isEnabled = false
        positive.setOnClickListener { if (threat) remove() else close() }
        negative.setOnClickListener { if (threat) keep() else remove() }

        fun append(text: String, color: Int) {
            val span = SpannableString(text + "\n")
            span.setSpan(ForegroundColorSpan(Voice.logColor(color)), 0, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            log.append(span)
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }

        // Tekshiruv logini qatorma-qator ko'rsatamiz, oxirida xulosa.
        lines.forEachIndexed { i, text ->
            handler.postDelayed({ append(text, colors.getOrElse(i) { Palette.GREEN }) }, i * LINE_MS)
        }
        handler.postDelayed({
            dialog.setTitle(intent.getStringExtra(EXTRA_TITLE))
            verdict.text = intent.getStringExtra(EXTRA_VERDICT)
            verdict.setTextColor(Voice.logColor(if (danger) Palette.RED else if (threat) Palette.YELLOW else Palette.GREEN))
            if (intent.getBooleanExtra(EXTRA_AUTO, false)) {
                append(Voice.AUTO_REMOVING, Palette.RED)
                handler.postDelayed({ remove() }, 1200)
            } else {
                positive.isEnabled = true
                negative.isEnabled = true
            }
        }, lines.size * LINE_MS + 200)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun remove() {
        startActivity(
            ThreatActionActivity.removeIntent(
                this, pkg, label, intent.getIntExtra(EXTRA_NOTIFICATION, 0),
                known = intent.getBooleanExtra(EXTRA_KNOWN, false), counted = threat,
            ),
        )
        finish()
    }

    /** Foydalanuvchi ilovaga ishonadi — u o'chirilmaguncha qayta tekshirilmaydi. */
    private fun keep() {
        val prefs = Prefs(this)
        prefs.trusted = prefs.trusted + pkg
        prefs.openThreats = (prefs.openThreats - 1).coerceAtLeast(0)
        Toast.makeText(this, Voice.keptToast(label), Toast.LENGTH_SHORT).show()
        close()
    }

    private fun close() {
        getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
