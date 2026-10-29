package uz.apkqoriqchi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File
import java.io.RandomAccessFile

/** Fayllarga kirish ruxsati (Android 11+ da "Barcha fayllarga ruxsat"). */
object Storage {
    const val REQUEST_CODE = 10

    val LEGACY_PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )

    /** true bo'lsa ruxsat Sozlamalar oynasi orqali olinadi, aks holda oddiy so'rov bilan. */
    val needsSettingsScreen get() = Build.VERSION.SDK_INT >= 30

    fun has(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun settingsIntents(ctx: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")),
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
    )
}

/** Tahdidni yo'q qilish uchun umumiy amallar. */
object Remover {
    private const val SHRED_LIMIT = 64L shl 20

    @Suppress("DEPRECATION")
    fun uninstallIntent(pkg: String): Intent =
        Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$pkg"))
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Faylni nol bilan qayta yozib (qayta tiklab bo'lmasin), keyin o'chiradi. */
    fun shredDelete(f: File, onStep: (String) -> Unit = {}): Boolean {
        if (!f.exists()) return true
        val len = f.length()
        if (len <= SHRED_LIMIT) {
            onStep("shred: ma'lumot nol bilan qayta yozilmoqda...")
            try {
                RandomAccessFile(f, "rw").use { raf ->
                    val zeros = ByteArray(64 * 1024)
                    var left = len
                    while (left > 0) {
                        val n = minOf(left, zeros.size.toLong()).toInt()
                        raf.write(zeros, 0, n)
                        left -= n
                    }
                }
            } catch (e: Exception) {
                onStep("shred imkonsiz: ${e.message}")
            }
        }
        onStep("unlink ${f.name}")
        return f.delete() || !f.exists()
    }
}
