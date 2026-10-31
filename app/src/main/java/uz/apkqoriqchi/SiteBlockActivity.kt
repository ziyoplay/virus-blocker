package uz.apkqoriqchi

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Brauzerda xavfli sayt ochilganda chiqadigan oyna. Aniq xavfli sayt allaqachon yopilgan bo'ladi
 * ([closed]) — oyna nima bo'lganini tushuntiradi. Shubhali saytda tanlov foydalanuvchida.
 */
class SiteBlockActivity : Activity() {

    companion object {
        /** Ogohlantirish ekranda — yangi saytlar u yopilgach tekshiriladi. */
        @Volatile var showing = false
            private set

        private const val EXTRA_URL = "url"
        private const val EXTRA_HOST = "host"
        private const val EXTRA_LEVEL = "level"
        private const val EXTRA_SCORE = "score"
        private const val EXTRA_REASONS = "reasons"
        private const val EXTRA_BROWSER = "browser"
        private const val EXTRA_CLOSED = "closed"

        fun intent(ctx: Context, v: SiteVerdict, browser: String, closed: Boolean): Intent =
            Intent(ctx, SiteBlockActivity::class.java)
                .putExtra(EXTRA_URL, v.url)
                .putExtra(EXTRA_HOST, v.host)
                .putExtra(EXTRA_LEVEL, v.level.name)
                .putExtra(EXTRA_SCORE, v.score)
                .putExtra(EXTRA_REASONS, v.reasons.toTypedArray())
                .putExtra(EXTRA_BROWSER, browser)
                .putExtra(EXTRA_CLOSED, closed)
                // Eski (javobsiz qolgan) ogohlantirish bo'lsa — uni yangisi bilan almashtiramiz.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val v = SiteVerdict(
            url = intent.getStringExtra(EXTRA_URL) ?: return finish(),
            host = intent.getStringExtra(EXTRA_HOST) ?: return finish(),
            level = ThreatLevel.valueOf(intent.getStringExtra(EXTRA_LEVEL) ?: ThreatLevel.SUSPICIOUS.name),
            score = intent.getIntExtra(EXTRA_SCORE, 0),
            reasons = intent.getStringArrayExtra(EXTRA_REASONS).orEmpty().toList(),
        )
        val closed = intent.getBooleanExtra(EXTRA_CLOSED, false)

        val dialog = AlertDialog.Builder(this, Voice.DIALOG_THEME)
            .setTitle(Voice.siteTitle(v, closed))
            .setMessage(Voice.siteText(v, closed))
            .setNegativeButton(Voice.SITE_OPEN) { _, _ -> openAnyway(v, closed) }
            .setOnCancelListener { finish() }
            .apply {
                if (closed) {
                    setPositiveButton(Voice.SITE_OK) { _, _ -> finish() }
                } else {
                    setPositiveButton(Voice.SITE_BACK) { _, _ ->
                        WebGuard.goBack()
                        finish()
                    }
                }
            }
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        this.dialog = dialog
    }

    /** Foydalanuvchi oynani tashlab ketsa (Home), u orqada osilib qolmasin — keyingi "orqaga" unga tushmasin. */
    override fun onStart() {
        super.onStart()
        showing = true
    }

    override fun onStop() {
        showing = false
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        dialog?.dismiss()
        super.onDestroy()
    }

    /** Foydalanuvchi saytni biladi: ilova yopilguncha u qayta to'xtatilmaydi. */
    private fun openAnyway(v: SiteVerdict, closed: Boolean) {
        WebGuard.allow(v.host)
        if (closed) {
            val uri = Uri.parse(if ("://" in v.url) v.url else "https://${v.url}")
            val view = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(Intent(view).setPackage(intent.getStringExtra(EXTRA_BROWSER)))
            } catch (e: Exception) {
                try {
                    startActivity(view)
                } catch (e: Exception) {
                }
            }
        }
        finish()
    }
}
