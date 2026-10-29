package uz.apkqoriqchi

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.io.RandomAccessFile

class Scanner(private val ctx: Context, private val con: Console = SilentConsole) {

    private val pm = ctx.packageManager

    var appsScanned = 0
        private set
    var filesScanned = 0
        private set

    // ───────────────────────── ILOVALAR ─────────────────────────

    fun scanApps(): List<Threat> {
        con.line()
        con.stage("Ilovalar tekshirilmoqda")
        con.line("──[ 1-BOSQICH: O'RNATILGAN ILOVALAR ]──", Palette.CYAN, 200)
        val all = pm.getInstalledApplications(0).filter { it.packageName != ctx.packageName }
        val system = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        val user = all.filter { it.flags and system == 0 }.sortedBy { it.packageName }
        con.line("[+] Jami: ${all.size}  |  foydalanuvchi: ${user.size}  |  tizim: ${all.size - user.size}", Palette.GREEN, 150)
        con.line("[*] Tizim ilovalari ishonchli imzo bilan — o'tkazib yuborildi", Palette.GREEN_DIM, 100)

        val found = mutableListOf<Threat>()
        user.forEachIndexed { i, ai ->
            con.progress(i, user.size)
            val label = ai.loadLabel(pm).toString()
            con.item(label)
            con.line("[scan] ${ai.packageName}", Palette.GREEN_DIM, 20)
            val threat = analyzeInstalled(ai, label)
            appsScanned++
            if (threat == null) {
                con.line("   └─ [ OK ] $label", Palette.GRAY, 10)
            } else {
                report(threat)
                found += threat
            }
        }
        con.progress(user.size, user.size)
        return found
    }

    /** Real-time: bitta yangi o'rnatilgan ilovani tekshirish. */
    fun analyzePackage(pkg: String): Threat? {
        if (pkg == ctx.packageName) return null
        val ai = try {
            pm.getApplicationInfo(pkg, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        if (ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) return null
        return analyzeInstalled(ai, ai.loadLabel(pm).toString())
    }

    /** Real-time: bitta yangi kelgan faylni tekshirish. */
    fun analyzeSingleFile(f: File): Threat? = analyzeFile(f, Environment.getExternalStorageDirectory())

    fun labelOf(pkg: String): String = try {
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    } catch (e: Exception) {
        pkg
    }

    @Suppress("DEPRECATION")
    private fun analyzeInstalled(ai: ApplicationInfo, label: String): Threat? {
        val pkg = ai.packageName
        val sigFlag = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = try {
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or sigFlag)
        } catch (e: Exception) {
            return null
        }

        val findings = mutableListOf<Finding>()
        Rules.signatureByPackage(pkg)?.let { findings += it }

        val (installer, initiator) = installSource(pkg)
        val trusted = (installer != null && installer in Rules.TRUSTED_INSTALLERS) || isVendorPreload(ai, installer)

        if (isDebugSigned(info)) findings += Finding(2, "Test (debug) kaliti bilan imzolangan", Tag.DEBUG)

        // Rasmiy do'kondan kelgan ilovalarga ruxsatlar bo'yicha ball qo'yilmaydi — aks holda
        // har bir messenjer "shubhali" bo'lib chiqadi.
        if (!trusted) {
            ai.sourceDir?.let { sha256(File(it)) }?.let { Rules.signatureByHash(it) }
                ?.takeIf { findings.none { f -> f.tag == Tag.SIGNATURE } }?.let { findings += it }
            findings += Finding(2, "Rasmiy do'kondan o'rnatilmagan" + sourceLabel(installer, initiator), Tag.SIDELOAD)
            if (initiator != null && initiator in Rules.MESSENGERS) {
                findings += Finding(1, "APK messenjer orqali yuborilgan", Tag.MESSENGER)
            }
            findings += Rules.permissionFindings(info)
            findings += Rules.componentFindings(info)
            if (pm.getLaunchIntentForPackage(pkg) == null) findings += Finding(2, "Ilova ikonkasi yashirilgan", Tag.HIDDEN)
            if (isAccessibilityEnabled(pkg)) findings += Finding(3, "Accessibility hozir FAOL", Tag.ACCESSIBILITY)
            if (isAdminActive(ctx, pkg)) findings += Finding(2, "Qurilma administratori hozir FAOL", Tag.ADMIN)
            Rules.impersonation(label, pkg)?.let { findings += it }
            Rules.fileLikeLabel(label)?.let { findings += it }
            Rules.lureName(label)?.let { findings += it }
        }
        return Rules.verdict(ThreatKind.APP, pkg, label, findings)
    }

    private fun installSource(pkg: String): Pair<String?, String?> = try {
        if (Build.VERSION.SDK_INT >= 30) {
            val src = pm.getInstallSourceInfo(pkg)
            src.installingPackageName to src.initiatingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(pkg) to null
        }
    } catch (e: Exception) {
        null to null
    }

    /**
     * Zavod (MIUI va boshq.) o'rnatgan ilovalar /data/app/POCOSTORE_OVERSEA kabi qat'iy nomli
     * papkada turadi. Oddiy o'rnatishda papka nomini tizim beradi va u paket nomi bilan boshlanadi,
     * shuning uchun zararli ilova buni soxtalashtira olmaydi.
     */
    private fun isVendorPreload(ai: ApplicationInfo, installer: String?): Boolean {
        if (installer != null) return false
        val dir = File(ai.sourceDir ?: return false).parentFile ?: return false
        return dir.parent == "/data/app" && !dir.name.startsWith(ai.packageName) && !dir.name.startsWith("~~")
    }

    private fun sourceLabel(installer: String?, initiator: String?): String {
        val who = initiator ?: installer ?: return " (adb / noma'lum)"
        return when {
            who.contains("telegram") || who.contains("challegram") -> " (Telegram'dan)"
            who.contains("packageinstaller") -> " (APK fayldan)"
            else -> " ($who)"
        }
    }

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): Array<Signature> {
        if (Build.VERSION.SDK_INT >= 28) {
            val si = info.signingInfo ?: return emptyArray()
            return (if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory) ?: emptyArray()
        }
        return info.signatures ?: emptyArray()
    }

    private fun isDebugSigned(info: PackageInfo): Boolean = signatures(info).any { sig ->
        try {
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(sig.toByteArray())) as X509Certificate
            cert.subjectX500Principal.name.contains("CN=Android Debug")
        } catch (e: Exception) {
            false
        }
    }

    private fun isAccessibilityEnabled(pkg: String): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.startsWith("$pkg/") }
    }

    // ───────────────────────── FAYLLAR ─────────────────────────

    fun scanFiles(): List<Threat> {
        con.line()
        con.stage("Fayllar tekshirilmoqda")
        con.line("──[ 2-BOSQICH: FAYL TIZIMI ]──", Palette.CYAN, 200)
        val root = Environment.getExternalStorageDirectory()
        con.line("[*] Ildiz: ${root.absolutePath}", Palette.GREEN, 80)
        con.line("[*] Fayllar indekslanmoqda...", Palette.GREEN, 80)

        val skip = setOf(File(root, "Android/data").absolutePath, File(root, "Android/obb").absolutePath)
        val files = ArrayList<File>()
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.addLast(root to 0)
        var dirs = 0
        while (stack.isNotEmpty() && files.size < MAX_FILES) {
            con.check()
            val (dir, depth) = stack.removeLast()
            val children = dir.listFiles() ?: continue
            dirs++
            if (dirs % 12 == 1) con.line("[idx ] ${short(dir, root)}/", Palette.GRAY)
            for (c in children) {
                if (c.isDirectory) {
                    if (depth < MAX_DEPTH && c.absolutePath !in skip) stack.addLast(c to depth + 1)
                } else {
                    files += c
                }
            }
        }
        con.line("[+] Indekslandi: ${files.size} fayl, $dirs papka", Palette.GREEN, 150)
        con.line("[*] Imzo va APK tahlili boshlandi...", Palette.GREEN, 100)

        val found = mutableListOf<Threat>()
        files.forEachIndexed { i, f ->
            if (i % 16 == 0) {
                con.progress(i, files.size)
                con.item(f.name)
            }
            filesScanned++
            val t = analyzeFile(f, root) ?: return@forEachIndexed
            report(t)
            found += t
        }
        con.progress(files.size, files.size)
        return found
    }

    private fun analyzeFile(f: File, root: File): Threat? {
        con.check()
        val len = f.length()
        if (len in 68..512 && isEicar(f)) {
            con.line("[sig ] ${short(f, root)}", Palette.RED, 60)
            return Rules.verdict(
                ThreatKind.FILE, f.absolutePath, f.name,
                listOf(Finding(10, "EICAR test-virus imzosi topildi", Tag.EICAR)),
            )
        }
        val namedApk = f.name.endsWith(".apk", ignoreCase = true)
        // Nomi "rasm.jpg" bo'lsa ham, ichida Android ilovasi bo'lishi mumkin.
        val hiddenApk = !namedApk && isApkInside(f)
        if (!namedApk && !hiddenApk) {
            if (filesScanned % 40 == 0) con.line("[file] ${short(f, root)}", Palette.GRAY)
            return null
        }

        con.line("[apk ] ${short(f, root)}", Palette.CYAN, 60)
        val findings = mutableListOf(Finding(2, "Tekshirilmagan APK fayl (${human(len)})", Tag.APK))
        if (hiddenApk) findings += Finding(6, "Yolg'on kengaytma: \"${f.name}\" ichida ilova (APK) bor", Tag.DOUBLE_EXT)
        sha256(f)?.let { Rules.signatureByHash(it) }?.let { findings += it }
        Rules.doubleExtension(f.name)?.let { findings += it }
        if (f.absolutePath.contains("telegram", ignoreCase = true)) {
            findings += Finding(1, "Telegram orqali yuklab olingan", Tag.MESSENGER)
        }

        var name = f.name
        @Suppress("DEPRECATION")
        val info = try {
            pm.getPackageArchiveInfo(f.absolutePath, PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS)
        } catch (e: Exception) {
            null
        }
        if (info == null) {
            findings += Finding(2, "APK tuzilmasi buzilgan / tahlil qilib bo'lmadi", Tag.BROKEN)
            Rules.lureName(f.name)?.let { findings += it }
        } else {
            info.applicationInfo?.let {
                it.sourceDir = f.absolutePath
                it.publicSourceDir = f.absolutePath
                name = "${it.loadLabel(pm)}  [${f.name}]"
            }
            val label = info.applicationInfo?.loadLabel(pm)?.toString() ?: f.name
            findings += Rules.permissionFindings(info)
            findings += Rules.componentFindings(info)
            Rules.impersonation(label, info.packageName)?.let { findings += it }
            Rules.fileLikeLabel(label)?.let { findings += it }
            (Rules.lureName(label) ?: Rules.lureName(f.name))?.let { findings += it }
        }
        val t = Rules.verdict(ThreatKind.FILE, f.absolutePath, name, findings)
        if (t == null) con.line("   └─ [ OK ] xavf past", Palette.GRAY, 20)
        return t
    }

    /**
     * ZIP sarlavhasi ("PK\u0003\u0004") va ichida AndroidManifest.xml + classes.dex nomlari bo'lsa — bu APK.
     * Zararli APK'lar ZIP tuzilmasini ataylab buzadi (ZipFile ochmaydi, Android o'rnatuvchisi esa ochadi),
     * shuning uchun nomlarni fayl boshidagi va oxiridagi (markaziy katalog) baytlardan to'g'ridan-to'g'ri qidiramiz.
     */
    private fun isApkInside(f: File): Boolean {
        val len = f.length()
        if (len < 4096) return false
        return try {
            RandomAccessFile(f, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (magic[0] != 0x50.toByte() || magic[1] != 0x4B.toByte() || magic[2] != 3.toByte() || magic[3] != 4.toByte()) {
                    return false
                }
                val head = ByteArray(minOf(len, 64L * 1024).toInt())
                raf.seek(0)
                raf.readFully(head)
                val tailSize = minOf(len, 2L * 1024 * 1024).toInt()
                val tail = ByteArray(tailSize)
                raf.seek(len - tailSize)
                raf.readFully(tail)
                val text = String(head, Charsets.ISO_8859_1) + String(tail, Charsets.ISO_8859_1)
                text.contains("AndroidManifest.xml") && text.contains("classes.dex")
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun sha256(f: File): String? = try {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        null
    }

    private fun isEicar(f: File): Boolean = try {
        String(f.readBytes(), Charsets.ISO_8859_1).contains("EICAR-STANDARD-ANTIVIRUS-TEST-FILE")
    } catch (e: Exception) {
        false
    }

    // ───────────────────────── QURILMA ─────────────────────────

    enum class Issue { ADB, DEV_MODE, NO_LOCK, ROOT, OLD_PATCH }

    fun deviceChecks(): List<Issue> {
        con.line()
        con.stage("Telefon sozlamalari tekshirilmoqda")
        con.line("──[ 3-BOSQICH: QURILMA XAVFSIZLIGI ]──", Palette.CYAN, 200)
        val cr = ctx.contentResolver
        val adb = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1
        val dev = Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        val locked = (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
        val rooted = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk", "/data/adb/magisk")
            .any { File(it).exists() }

        val issues = mutableListOf<Issue>()
        check(issues, Issue.ADB, "USB debugging (ADB)", !adb, "yoqilgan — kompyuter orqali kirish mumkin")
        check(issues, Issue.DEV_MODE, "Dasturchi rejimi", !dev, "yoqilgan")
        check(issues, Issue.NO_LOCK, "Ekran qulfi", locked, "o'rnatilmagan!")
        check(issues, Issue.ROOT, "Root huquqi", !rooted, "qurilma ROOT qilingan")
        check(issues, Issue.OLD_PATCH, "Xavfsizlik yangilanishi", patchIsFresh(), "${Build.VERSION.SECURITY_PATCH} — eskirgan")
        return issues
    }

    private fun check(issues: MutableList<Issue>, issue: Issue, title: String, ok: Boolean, problem: String) {
        if (ok) {
            con.line("[ OK ] $title", Palette.GREEN_DIM, 90)
        } else {
            issues += issue
            con.line("[ !! ] $title: $problem", Palette.YELLOW, 120)
        }
    }

    private fun patchIsFresh(): Boolean = try {
        val patch = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(Build.VERSION.SECURITY_PATCH)!!
        val yearAgo = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }.time
        patch.after(yearAgo)
    } catch (e: Exception) {
        true
    }

    // ───────────────────────── HISOBOT ─────────────────────────

    private fun report(t: Threat) {
        val malware = t.level == ThreatLevel.MALWARE
        val c = if (malware) Palette.RED else Palette.YELLOW
        val dim = if (malware) Palette.RED_DIM else Palette.YELLOW_DIM
        con.line(if (malware) "[!!!] MALWARE ANIQLANDI ━━━━━━━━━━━━" else "[ ! ] SHUBHALI OBYEKT ━━━━━━━━━━━━━", c, 150)
        con.line("      nomi : ${t.name}", c, 60)
        con.line("      manba: ${t.target}", c, 60)
        con.line("      turi : ${t.family}", c, 60)
        con.line("      xavf : ${Art.bar(t.score.coerceAtMost(16), 16, 16)} ${t.score}", c, 60)
        t.reasons.forEachIndexed { i, r ->
            con.line("      ${if (i == t.reasons.lastIndex) "└─" else "├─"} $r", dim, 45)
        }
    }

    private fun short(f: File, root: File): String =
        f.absolutePath.removePrefix(root.absolutePath).ifEmpty { "/" }

    companion object {
        private const val MAX_DEPTH = 8
        private const val MAX_FILES = 60_000

        fun human(bytes: Long): String = when {
            bytes >= 1 shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
            bytes >= 1 shl 10 -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }

        fun isAdminActive(ctx: Context, pkg: String): Boolean {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.activeAdmins?.any { it.packageName == pkg } == true
        }
    }
}
