package uz.apkqoriqchi

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

object Realtime {
    private const val REQ_NOTIFICATIONS = 40

    val isXiaomi: Boolean
        get() = listOf("xiaomi", "redmi", "poco").any {
            Build.MANUFACTURER.equals(it, true) || Build.BRAND.equals(it, true)
        }

    fun enable(a: Activity) {
        Prefs(a).realtime = true
        if (Build.VERSION.SDK_INT >= 33 &&
            a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            a.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
        GuardService.start(a)
    }

    fun disable(ctx: Context) {
        Prefs(ctx).realtime = false
        GuardService.stop(ctx)
    }

    /** Ilova ochilganda: himoya yoqilgan bo'lsa, xizmat ishlab turganiga ishonch hosil qilamiz. */
    fun resume(ctx: Context) {
        if (Prefs(ctx).realtime) GuardService.start(ctx)
    }

    fun openAutostart(a: Activity) {
        val miui = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        try {
            a.startActivity(miui)
        } catch (e: Exception) {
            a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")))
        }
    }
}

/**
 * Himoyani yoqqandan keyingi sozlash qadamlari: fayllarga ruxsat, so'ng (Xiaomi'da) avtozapusk.
 * Matnlar versiyaga qarab beriladi.
 */
class GuardSetup(private val a: Activity, private val texts: Texts) {

    class Texts(
        val storageTitle: String,
        val storageMessage: String,
        val autostartTitle: String,
        val autostartMessage: String,
        val ok: String,
        val later: String,
    )

    private var pendingAutostart = false

    fun start() {
        if (Storage.has(a)) askAutostart() else askStorage()
    }

    fun onResume() {
        if (pendingAutostart) {
            pendingAutostart = false
            Realtime.resume(a)
            askAutostart()
        }
    }

    private fun askStorage() {
        if (!Storage.needsSettingsScreen) {
            a.requestPermissions(Storage.LEGACY_PERMISSIONS, Storage.REQUEST_CODE)
            askAutostart()
            return
        }
        AlertDialog.Builder(a)
            .setTitle(texts.storageTitle)
            .setMessage(texts.storageMessage)
            .setPositiveButton(texts.ok) { _, _ ->
                pendingAutostart = true
                Storage.settingsIntents(a).any {
                    try {
                        a.startActivity(it)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
            }
            .setNegativeButton(texts.later) { _, _ -> askAutostart() }
            .show()
    }

    private fun askAutostart() {
        if (!Realtime.isXiaomi) return
        val prefs = Prefs(a)
        if (prefs.autostartHintShown) return
        prefs.autostartHintShown = true
        AlertDialog.Builder(a)
            .setTitle(texts.autostartTitle)
            .setMessage(texts.autostartMessage)
            .setPositiveButton(texts.ok) { _, _ -> Realtime.openAutostart(a) }
            .setNegativeButton(texts.later, null)
            .show()
    }
}

/** Telefon qayta yoqilganda yoki ilova yangilanganda himoyani qayta ishga tushiradi. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Realtime.resume(context)
        }
    }
}
