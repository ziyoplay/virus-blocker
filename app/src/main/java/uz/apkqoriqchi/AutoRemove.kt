package uz.apkqoriqchi

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.concurrent.thread

/**
 * Avtomatik o'chirish. Android oddiy ilovaga boshqa ilovani tasdiqsiz o'chirishga ruxsat bermaydi,
 * shuning uchun tizimning "O'chirilsinmi?" oynasidagi tasdiq tugmasini maxsus imkoniyatlar
 * (Accessibility) xizmati bosadi.
 *
 * Xavfsizlik: tugma faqat Qo'riqchi o'zi so'nggi [ARM_MS] ichida ochgan o'chirish oynasida, faqat
 * oynada o'sha ilova nomi va "o'chirish" so'zi bo'lsa bosiladi. Boshqa oynalar o'qilmaydi
 * (web himoya yoqilgan bo'lsa — brauzerning manzil satri ham, qarang [WebGuard]).
 */
object AutoConfirm {
    private const val ARM_MS = 20_000L
    private const val MAX_NODES = 300

    class Target(val pkg: String, val label: String, val until: Long)

    @Volatile private var target: Target? = null

    /** O'chirish oynasini ochishdan oldin chaqiriladi. */
    fun arm(pkg: String, label: String) {
        target = Target(pkg, label, SystemClock.uptimeMillis() + ARM_MS)
    }

    fun disarm() {
        target = null
    }

    fun armed(): Target? = target?.takeIf { SystemClock.uptimeMillis() < it.until }

    fun isEnabled(ctx: Context): Boolean {
        val me = ComponentName(ctx, AutoConfirmService::class.java)
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Shu telefonda "O'chirilsinmi?" oynasini chiqaradigan tizim ilovalari. */
    fun installerPackages(ctx: Context): Set<String> {
        val resolved = try {
            ctx.packageManager.queryIntentActivities(Remover.uninstallIntent(ctx.packageName), PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo.packageName }
        } catch (e: Exception) {
            emptyList()
        }
        return (resolved + KNOWN_INSTALLERS).filter { it != "android" }.toSet()
    }

    private val KNOWN_INSTALLERS = listOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.samsung.android.packageinstaller",
    )

    private val UNINSTALL_WORDS = listOf(
        "uninstall", "delete", "remove", "o'chir", "o‘chir", "oʻchir", "o`chir", "удал", "видал", "deinstall", "désinstall", "desinstal",
    )
    private val INSTALL_WORDS = listOf("install", "o'rnat", "o‘rnat", "oʻrnat", "установ", "встанов")
    private val CONFIRM_WORDS = setOf(
        "ok", "ок", "uninstall", "delete", "remove", "o'chirish", "o‘chirish", "oʻchirish", "удалить", "да", "yes", "ha",
    )

    /** Oynada nishon ilovani o'chirish so'ralayotgan bo'lsa — tasdiq tugmasini bosadi. */
    fun tryConfirm(root: AccessibilityNodeInfo, t: Target): Boolean {
        val nodes = flatten(root)
        val text = nodes.joinToString("\n") { "${it.text ?: ""} ${it.contentDescription ?: ""}" }.lowercase()
        val named = (t.label.isNotBlank() && t.label.lowercase() in text) || t.pkg in text
        if (!named) return false
        if (UNINSTALL_WORDS.none { it in text }) return false

        val button = root.findAccessibilityNodeInfosByViewId("android:id/button1").firstOrNull { it.isEnabled }
            ?: nodes.firstOrNull { it.isEnabled && it.text?.toString()?.trim()?.lowercase() in CONFIRM_WORDS }
            ?: return false
        // Hech qachon "O'rnatish" tugmasini bosmaymiz (masalan, virus bir vaqtda boshqa APK o'rnatmoqchi bo'lsa).
        val label = button.text?.toString()?.lowercase() ?: ""
        if (INSTALL_WORDS.any { it in label } && UNINSTALL_WORDS.none { it in label }) return false
        var n: AccessibilityNodeInfo? = button
        while (n != null && !n.isClickable) n = n.parent
        return n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun flatten(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty() && out.size < MAX_NODES) {
            val n = stack.removeLast()
            out += n
            for (i in n.childCount - 1 downTo 0) n.getChild(i)?.let { stack.addLast(it) }
        }
        return out
    }
}

class AutoConfirmService : AccessibilityService() {

    companion object {
        @Volatile var instance: AutoConfirmService? = null
            private set

        /** Brauzer oynasi o'zgarishlari ko'p keladi — manzil satrini shu oraliqda bir marta o'qiymiz. */
        private const val URL_READ_DELAY_MS = 300L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var installers: Set<String> = emptySet()
    private var browsers: Set<String> = emptySet()
    private var browserPkg: String? = null
    private var readPending = false
    private val readUrl = Runnable {
        readPending = false
        browserPkg?.let { WebGuard.inspect(this, it) }
    }

    override fun onServiceConnected() {
        instance = this
        installers = AutoConfirm.installerPackages(this)
        browsers = WebGuard.browserPackages(this)
        refreshScope()
        thread { WebDb.load(this) }
    }

    /**
     * Faqat o'chirish oynasini chiqaradigan tizim ilovalaridan va (web himoya yoqilgan bo'lsa) brauzerlardan
     * xabar olamiz — boshqa ilovalar ko'rinmaydi. Brauzerdan faqat manzil satri o'qiladi.
     */
    fun refreshScope() {
        val scope = if (WebGuard.isOn(this)) installers + browsers else installers
        serviceInfo = serviceInfo.apply { packageNames = scope.toTypedArray() }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        handler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        if (pkg != null && pkg in browsers) {
            if (!WebGuard.isOn(this)) return
            browserPkg = pkg
            if (!readPending) {
                readPending = true
                handler.postDelayed(readUrl, URL_READ_DELAY_MS)
            }
            return
        }
        val t = AutoConfirm.armed() ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() !in installers) return
        if (AutoConfirm.tryConfirm(root, t)) AutoConfirm.disarm()
    }

    override fun onInterrupt() {}
}
