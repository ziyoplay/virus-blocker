package uz.apkqoriqchi

import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import java.io.File
import kotlin.concurrent.thread

/**
 * Ko'rinmas oyna: tahdidni o'chirib, darhol yopiladi. Bildirishnomadagi "O'chirish" tugmasi yoki
 * yangi ilova oynasi ([CheckActivity]) ochadi. Ilova o'chgach, uning qoldiqlari ham tozalanadi.
 */
class ThreatActionActivity : Activity() {

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_NOTIFICATION = "notification"
        private const val EXTRA_KNOWN = "known"
        /** Tahdid hisoblagichiga (openThreats) qo'shilganmi — toza ilova uchun false. */
        private const val EXTRA_COUNTED = "counted"
        private const val REQ_UNINSTALL = 31

        fun intent(ctx: Context, t: Threat, notificationId: Int): Intent =
            base(ctx, t.kind, t.target, t.name.substringBefore("  ["), notificationId, Tag.SIGNATURE in t.tags, true)

        /** Ilovani o'chirish. [known] — imzolar bazasidagi virus, [counted] — tahdid hisoblagichida turibdi. */
        fun removeIntent(ctx: Context, pkg: String, label: String, notificationId: Int, known: Boolean, counted: Boolean): Intent =
            base(ctx, ThreatKind.APP, pkg, label, notificationId, known, counted)

        private fun base(
            ctx: Context, kind: ThreatKind, target: String, name: String, notificationId: Int, known: Boolean, counted: Boolean,
        ): Intent =
            Intent(ctx, ThreatActionActivity::class.java)
                .putExtra(EXTRA_KIND, kind.name)
                .putExtra(EXTRA_TARGET, target)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_NOTIFICATION, notificationId)
                .putExtra(EXTRA_KNOWN, known)
                .putExtra(EXTRA_COUNTED, counted)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private lateinit var target: String
    private lateinit var name: String
    private var leftovers: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        target = intent.getStringExtra(EXTRA_TARGET) ?: return finish()
        name = intent.getStringExtra(EXTRA_NAME) ?: target
        remove()
    }

    private fun remove() {
        if (intent.getStringExtra(EXTRA_KIND) == ThreatKind.FILE.name) {
            thread {
                val ok = Remover.shredDelete(File(target))
                runOnUiThread { done(ok, 0) }
            }
            return
        }
        val known = intent.getBooleanExtra(EXTRA_KNOWN, false)
        thread {
            val found = Remover.leftovers(this, target, known)
            runOnUiThread {
                leftovers = found
                (getSystemService(ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(target)
                AutoConfirm.arm(target, name)
                try {
                    startActivityForResult(Remover.uninstallIntent(target), REQ_UNINSTALL)
                } catch (e: Exception) {
                    done(false, 0)
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_UNINSTALL) return
        AutoConfirm.disarm()
        if (Remover.isInstalled(this, target)) return done(false, 0)
        thread {
            val cleaned = Remover.cleanLeftovers(leftovers, target)
            runOnUiThread { done(true, cleaned) }
        }
    }

    private fun done(ok: Boolean, cleaned: Int) {
        val prefs = Prefs(this)
        if (ok) {
            if (intent.getBooleanExtra(EXTRA_COUNTED, false)) {
                prefs.totalRemoved = prefs.totalRemoved + 1
                prefs.openThreats = (prefs.openThreats - 1).coerceAtLeast(0)
            }
            // Muvaffaqiyatsiz bo'lsa ogohlantirish "O'chirish" tugmasi bilan qoladi.
            getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
        }
        Toast.makeText(this, if (ok) Voice.removedToast(name, cleaned) else Voice.notRemovedToast(name), Toast.LENGTH_LONG).show()
        finish()
    }
}
