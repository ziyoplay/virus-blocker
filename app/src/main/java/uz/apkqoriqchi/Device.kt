package uz.apkqoriqchi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import java.io.File
import java.io.RandomAccessFile

/** Fayllarga kirish ruxsati (Android 11+ da "Barcha fayllarga ruxsat"). */
object Storage {
    const val REQUEST_CODE = 10

    /** Fayllar odatda keladigan papkalar (tashqi xotira ildiziga nisbatan). */
    val WATCH_BASES = listOf(
        "Download",
        "Telegram",
        "Android/media/org.telegram.messenger/Telegram",
        "Android/media/org.telegram.messenger.web/Telegram",
        "Android/media/com.whatsapp/WhatsApp/Media",
        "Bluetooth",
    )

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

    /**
     * Ilovani o'chirishdan OLDIN chaqiriladi (ishchi oqimda). Topadi:
     *  - uni o'rnatgan APK nusxalarini (Telegram, Download ...) — paket nomi bo'yicha;
     *  - ilova umumiy xotiraga yozgan fayllarni (u o'chgach, MediaStore'da egasi unutiladi).
     *    Aniq virus bo'lsa — hammasi, aks holda faqat ilova/kod fayllari (rasm-videolarga tegmaymiz).
     */
    fun leftovers(ctx: Context, pkg: String, everything: Boolean): List<File> {
        if (!Storage.has(ctx)) return emptyList()
        val out = linkedSetOf<File>()
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                @Suppress("DEPRECATION")
                ctx.contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    arrayOf(MediaStore.MediaColumns.DATA),
                    "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?",
                    arrayOf(pkg),
                    null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val f = File(c.getString(0) ?: continue)
                        if (f.isFile && (everything || isCode(f))) out += f
                    }
                }
            } catch (e: Exception) {
                // MediaStore band — o'rnatuvchi nusxalarni baribir qidiramiz.
            }
        }
        val root = Environment.getExternalStorageDirectory()
        for (base in Storage.WATCH_BASES) {
            File(root, base).walkTopDown().maxDepth(4).filter { it.isFile && isCode(it) }.forEach { f ->
                @Suppress("DEPRECATION")
                val owner = try {
                    ctx.packageManager.getPackageArchiveInfo(f.absolutePath, 0)?.packageName
                } catch (e: Exception) {
                    null
                }
                if (owner == pkg) out += f
            }
        }
        return out.toList()
    }

    /**
     * Ilova o'chgandan KEYIN: [leftovers] topgan fayllarni yo'q qiladi va Android/data|media|obb
     * ichidagi papkalarini o'chiradi (ichki xotira va keshni tizimning o'zi o'chiradi). Nechta narsa tozalanganini qaytaradi.
     */
    fun cleanLeftovers(files: List<File>, pkg: String, onStep: (String) -> Unit = {}): Int {
        var cleaned = 0
        for (f in files) {
            if (!f.exists()) continue
            onStep("qoldiq: ${f.absolutePath}")
            if (shredDelete(f)) cleaned++
        }
        val root = Environment.getExternalStorageDirectory()
        for (dir in listOf("Android/data", "Android/media", "Android/obb").map { File(root, "$it/$pkg") }) {
            if (!dir.exists()) continue
            onStep("rm -rf ${dir.absolutePath}")
            if (dir.deleteRecursively()) cleaned++
        }
        return cleaned
    }

    private fun isCode(f: File): Boolean {
        val n = f.name.lowercase()
        return n.endsWith(".apk") || n.endsWith(".dex") || n.endsWith(".jar") || Scanner.isApkInside(f)
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
