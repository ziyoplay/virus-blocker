package uz.apkqoriqchi

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import java.io.File
import kotlin.concurrent.thread

/** Bildirishnomadagi "O'chirish" tugmasi: ko'rinmas oyna, tahdidni o'chirib, darhol yopiladi. */
class ThreatActionActivity : Activity() {

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_NOTIFICATION = "notification"
        private const val REQ_UNINSTALL = 31

        fun intent(ctx: Context, t: Threat, notificationId: Int): Intent =
            Intent(ctx, ThreatActionActivity::class.java)
                .putExtra(EXTRA_KIND, t.kind.name)
                .putExtra(EXTRA_TARGET, t.target)
                .putExtra(EXTRA_NAME, t.name.substringBefore("  ["))
                .putExtra(EXTRA_NOTIFICATION, notificationId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private lateinit var target: String
    private lateinit var name: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        target = intent.getStringExtra(EXTRA_TARGET) ?: return finish()
        name = intent.getStringExtra(EXTRA_NAME) ?: target
        getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))

        if (intent.getStringExtra(EXTRA_KIND) == ThreatKind.FILE.name) {
            thread {
                val ok = Remover.shredDelete(File(target))
                runOnUiThread { done(ok) }
            }
        } else {
            try {
                startActivityForResult(Remover.uninstallIntent(target), REQ_UNINSTALL)
            } catch (e: Exception) {
                done(false)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_UNINSTALL) done(!Remover.isInstalled(this, target))
    }

    private fun done(ok: Boolean) {
        val prefs = Prefs(this)
        if (ok) {
            prefs.totalRemoved = prefs.totalRemoved + 1
            prefs.openThreats = (prefs.openThreats - 1).coerceAtLeast(0)
        }
        Toast.makeText(this, if (ok) Voice.removedToast(name) else Voice.notRemovedToast(name), Toast.LENGTH_LONG).show()
        finish()
    }
}
