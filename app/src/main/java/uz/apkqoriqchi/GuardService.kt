package uz.apkqoriqchi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder

import android.util.Log
import java.io.File

/**
 * Real-time himoya: har bir yangi o'rnatilgan ilovani qattiq rejimda tekshirib, natijani oynada
 * ko'rsatadi; Telegram/WhatsApp/Download papkalariga kelgan fayllarni tekshiradi, aniq virusni o'chiradi.
 * Matnlar har bir versiyaning [Voice] obyektidan olinadi.
 */
class GuardService : Service() {

    companion object {
        private const val CH_STATUS = "guard_status"
        private const val CH_ALERT = "guard_alert"
        private const val CH_INFO = "guard_info"
        private const val ONGOING_ID = 1
        private const val INFO_ID = 2
        private const val REFRESH_MS = 5 * 60_000L
        private const val TAG = "Qoriqchi"

        /** Fayl uchun bu belgilar xato bo'lmaydi — so'ramasdan o'chiriladi. */
        private val AUTO_FILE_TAGS = setOf(Tag.SIGNATURE, Tag.EICAR, Tag.DOUBLE_EXT, Tag.FAKE_FILE, Tag.EVASION)

        fun start(ctx: Context) {
            try {
                ctx.startForegroundService(Intent(ctx, GuardService::class.java))
            } catch (e: Exception) {
                // Fondan ishga tushirish taqiqlangan holat — ilova ochilganda qayta uriniladi.
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, GuardService::class.java))
        }
    }

    private lateinit var nm: NotificationManager
    private lateinit var prefs: Prefs
    private lateinit var scanner: Scanner
    private val worker = HandlerThread("guard")
    private lateinit var bg: Handler
    private val observers = HashMap<String, FileObserver>()
    private val recent = HashMap<String, String>()
    private val pendingSizes = HashMap<String, Long>()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val pkg = i.data?.schemeSpecificPart ?: return
            if (i.action == Intent.ACTION_PACKAGE_FULLY_REMOVED) {
                // "Qoldirilgan" ilova o'chirildi — shu nom bilan keyin kelgan boshqa ilova qayta tekshirilsin.
                if (pkg in prefs.trusted) prefs.trusted = prefs.trusted - pkg
                return
            }
            // O'rnatish manbasi tizimda yozilib ulgurishi uchun biroz kutamiz.
            val replacing = i.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            bg.postDelayed({ checkPackage(pkg, replacing) }, 1500)
        }
    }

    private val refresh = object : Runnable {
        override fun run() {
            refreshObservers()
            bg.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        prefs = Prefs(this)
        createChannels()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ONGOING_ID, ongoing(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ONGOING_ID, ongoing())
        }

        worker.start()
        bg = Handler(worker.looper)
        scanner = Scanner(this)

        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packageReceiver, filter, RECEIVER_EXPORTED)
        else registerReceiver(packageReceiver, filter)
        bg.post(refresh)
        contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, mediaObserver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ruxsat yangi berilgan bo'lishi mumkin — papkalarni qayta ulaymiz.
        if (::bg.isInitialized) bg.post { refreshObservers() }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        contentResolver.unregisterContentObserver(mediaObserver)
        bg.removeCallbacksAndMessages(null)
        bg.post {
            observers.values.forEach { it.stopWatching() }
            observers.clear()
        }
        worker.quitSafely()
        super.onDestroy()
    }

    // ───────────────────────── Kuzatish ─────────────────────────

    private fun watchDirs(): Set<String> {
        val root = Environment.getExternalStorageDirectory()
        val out = mutableSetOf<String>()
        // Kuzatiladi: har bir asosiy papka va uning bevosita ichki papkalari.
        for (base in Storage.WATCH_BASES.map { File(root, it) }.filter { it.isDirectory }) {
            out += base.absolutePath
            base.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { out += it.absolutePath }
        }
        return out
    }

    private fun refreshObservers() {
        if (!Storage.has(this)) return
        val dirs = watchDirs()
        observers.keys.filter { it !in dirs }.forEach { observers.remove(it)?.stopWatching() }
        for (dir in dirs) {
            if (dir in observers) continue
            @Suppress("DEPRECATION")
            val o = object : FileObserver(dir, CLOSE_WRITE or MOVED_TO or CREATE) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == null) return
                    val f = File(dir, path)
                    if (event and CREATE != 0) {
                        if (f.isDirectory) bg.post { refreshObservers() }
                        return
                    }
                    bg.postDelayed({ checkFile(f) }, 800)
                }
            }
            o.startWatching()
            observers[dir] = o
        }
    }

    /**
     * Telefon xotirasining istalgan joyiga yangi fayl tushsa, MediaStore xabar beradi.
     * Papkalarni birma-bir kuzatishdan farqli o'laroq, bu butun xotirani qamraydi va batareyani yemaydi.
     */
    private val mediaObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            scheduleSweep(1500)
        }
    }

    private val sweepNewMedia = Runnable { sweepNewMedia() }

    private fun scheduleSweep(delayMs: Long) {
        bg.removeCallbacks(sweepNewMedia)
        bg.postDelayed(sweepNewMedia, delayMs)
    }

    /**
     * Oxirgi 3 daqiqada qo'shilgan barcha fayllarni ko'rib chiqadi. Hali yozilayotgan ("pending")
     * fayl bo'lsa, 2 soniyadan keyin yana qaraymiz. O'zgarmagan fayl [checkFile] da qayta tahlil qilinmaydi.
     */
    private fun sweepNewMedia() {
        if (!Storage.has(this)) return
        val pendingCol = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.IS_PENDING else null
        @Suppress("DEPRECATION")
        val projection = listOfNotNull(MediaStore.MediaColumns.DATA, pendingCol).toTypedArray()
        val since = System.currentTimeMillis() / 1000 - 180
        val selection = "${MediaStore.MediaColumns.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf(since.toString())
        var stillWriting = false
        try {
            val cursor = if (Build.VERSION.SDK_INT >= 30) {
                val args = Bundle().apply {
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                }
                contentResolver.query(MediaStore.Files.getContentUri("external"), projection, args, null)
            } else {
                contentResolver.query(MediaStore.Files.getContentUri("external"), projection, selection, selectionArgs, null)
            }
            cursor?.use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(0) ?: continue
                    val f = File(path)
                    // "Pending" belgisi ba'zan hech qachon olinmaydi (masalan, .jpg nomli, lekin rasm emas fayl).
                    // Shuning uchun fayl o'lchami ikki qarashda bir xil bo'lsa — yozib bo'lingan deb hisoblaymiz.
                    if (pendingCol != null && c.getInt(1) == 1) {
                        val size = f.length()
                        if (size == 0L || pendingSizes[path] != size) {
                            pendingSizes[path] = size
                            stillWriting = true
                            continue
                        }
                    }
                    pendingSizes.remove(path)
                    checkFile(f)
                }
            }
        } catch (e: Exception) {
            // MediaStore vaqtincha band bo'lishi mumkin — keyingi o'zgarishda qayta uriniladi.
            Log.w(TAG, "MediaStore so'rovi bajarilmadi", e)
        }
        if (stillWriting) scheduleSweep(2000)
    }

    private fun checkFile(f: File) {
        if (!f.isFile) return
        val isApk = f.name.endsWith(".apk", ignoreCase = true)
        // Bir fayl ham papka kuzatuvchisidan, ham MediaStore'dan kelishi mumkin — o'zgarmagan bo'lsa qayta tekshirmaymiz.
        val stamp = "${f.length()}:${f.lastModified()}"
        if (recent[f.absolutePath] == stamp) return
        if (recent.size > 2000) recent.clear()
        recent[f.absolutePath] = stamp

        val t = try {
            scanner.analyzeSingleFile(f)
        } catch (e: Exception) {
            Log.w(TAG, "faylni tahlil qilib bo'lmadi: $f", e)
            null
        }
        when {
            t != null && t.target !in prefs.trusted -> alert(t)
            isApk -> info(Voice.fileCleanText(f.name))
        }
    }

    /**
     * Yangi ilova — qayerdan kelganidan qat'i nazar qattiq tekshiriladi va natija oynada ko'rsatiladi:
     * log, risklar, xulosa va "Qoldirish / O'chirish". Messenjerdan kelgan aniq virus o'zi o'chiriladi.
     * Yangilanish toza bo'lsa oyna chiqmaydi (Play har kuni o'nlab ilovani yangilaydi).
     */
    private fun checkPackage(pkg: String, replacing: Boolean) {
        if (pkg == packageName) return
        val ai = try {
            packageManager.getApplicationInfo(pkg, 0)
        } catch (e: Exception) {
            return
        }
        if (ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) return
        val log = LogConsole()
        val checker = Scanner(this, log)
        val source = checker.sourceOf(ai)
        val t = try {
            checker.analyzePackage(pkg)
        } catch (e: Exception) {
            log.line("[!] tahlil xatosi: ${e.message}", Palette.YELLOW)
            null
        }
        if (replacing && (t == null || pkg in prefs.trusted)) return
        val auto = t?.level == ThreatLevel.MALWARE && source == Scanner.Source.MESSENGER && AutoConfirm.isEnabled(this)
        showCheck(pkg, checker.labelOf(pkg), t, log, auto)
    }

    // ───────────────────────── Bildirishnomalar ─────────────────────────

    private fun createChannels() {
        nm.createNotificationChannel(NotificationChannel(CH_STATUS, Voice.CHANNEL_STATUS, NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, Voice.CHANNEL_ALERT, NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_INFO, Voice.CHANNEL_INFO, NotificationManager.IMPORTANCE_LOW))
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ongoing(): Notification = Notification.Builder(this, CH_STATUS)
        .setSmallIcon(R.drawable.ic_notify)
        .setContentTitle(Voice.ONGOING_TITLE)
        .setContentText(Voice.ONGOING_TEXT)
        .setOngoing(true)
        .setContentIntent(openApp())
        .build()

    private fun alert(t: Threat) {
        val id = 1000 + (t.target.hashCode() and 0xFFFFF)
        val remove = PendingIntent.getActivity(
            this, id, ThreatActionActivity.intent(this, t, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = Voice.alertText(t)
        val n = Notification.Builder(this, CH_ALERT)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFFD64545.toInt())
            .setContentTitle(Voice.alertTitle(t))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notify), Voice.REMOVE, remove).build())
            .build()
        nm.notify(id, n)
        prefs.openThreats = prefs.openThreats + 1
        if (t.level == ThreatLevel.MALWARE) autoRemoveFile(t, id)
    }

    /** Aniq virus fayli (ma'lum imzo, yolg'on kengaytma, chalkashtirilgan APK) — so'ramasdan yo'q qilinadi. */
    private fun autoRemoveFile(t: Threat, id: Int) {
        if (t.tags.none { it in AUTO_FILE_TAGS } || !Remover.shredDelete(File(t.target))) return
        prefs.totalRemoved = prefs.totalRemoved + 1
        prefs.openThreats = (prefs.openThreats - 1).coerceAtLeast(0)
        val text = Voice.autoRemovedText(t)
        nm.notify(
            id,
            Notification.Builder(this, CH_ALERT)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(Voice.autoRemovedTitle(t))
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build(),
        )
    }

    /**
     * Yangi ilova oynasini ochadi (Accessibility yoqilgan bo'lsa fonda ham). Ochilmasa — bildirishnoma
     * qoladi, uni bosib oynani ochish mumkin.
     */
    private fun showCheck(pkg: String, label: String, t: Threat?, log: LogConsole, auto: Boolean) {
        val id = 1000 + (pkg.hashCode() and 0xFFFFF)
        val check = CheckActivity.intent(this, pkg, label, t, log, id, auto)
        val text = Voice.verdictText(label, t)
        val b = Notification.Builder(this, if (t != null) CH_ALERT else CH_INFO)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(Voice.checkTitle(label, t))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(this, id, check, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        if (t != null) {
            val remove = ThreatActionActivity.removeIntent(this, pkg, label, id, Tag.SIGNATURE in t.tags, counted = true)
            b.setColor(0xFFD64545.toInt()).addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_notify), Voice.REMOVE,
                    PendingIntent.getActivity(this, id + 1, remove, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                ).build(),
            )
            prefs.openThreats = prefs.openThreats + 1
        }
        nm.notify(id, b.build())
        try {
            startActivity(check)
        } catch (e: Exception) {
            Log.w(TAG, "tekshiruv oynasi ochilmadi", e)
        }
    }

    private fun info(text: String) {
        val n = Notification.Builder(this, CH_INFO)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(Voice.CLEAN_TITLE)
            .setContentText(text)
            .setAutoCancel(true)
            .setTimeoutAfter(10_000)
            .setContentIntent(openApp())
            .build()
        nm.notify(INFO_ID, n)
    }
}
