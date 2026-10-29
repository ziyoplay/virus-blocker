package uz.apkqoriqchi

import android.content.pm.PackageInfo

enum class ThreatKind { APP, FILE }

enum class ThreatLevel { MALWARE, SUSPICIOUS }

enum class Tag { SIGNATURE, BANKER, DROPPER, INSTALLER, FAKE_FILE, VPN, SMS, ACCESSIBILITY, ADMIN, NOTIF, HIDDEN, SIDELOAD, MESSENGER, DEBUG, FAKE, LURE, DOUBLE_EXT, APK, BROKEN, EICAR, PERM }

class Finding(val points: Int, val text: String, val tag: Tag, val meta: String? = null)

data class Threat(
    val kind: ThreatKind,
    /** Paket nomi (APP) yoki faylning to'liq yo'li (FILE). */
    val target: String,
    val name: String,
    val family: String,
    val level: ThreatLevel,
    val score: Int,
    val reasons: List<String>,
    val tags: Set<Tag>,
    /** Soxta ilova bo'lsa — qaysi brendga taqlid qilgani. */
    val brand: String?,
)

/** Evristik qoidalar: har bir belgi ball qo'shadi, yig'indi bo'yicha hukm chiqariladi. */
object Rules {

    const val MALWARE_SCORE = 8
    const val SUSPICIOUS_SCORE = 5

    /**
     * Imzolar bazasi: o'zimiz tahlil qilib, zararli ekani tasdiqlangan namunalar.
     * Nomi o'zgartirib qayta yuborilsa ham xesh / paket nomi bo'yicha aniq topiladi.
     */
    private val KNOWN_BAD_SHA256 = mapOf(
        // "To`ydan arxivi(7).jpeg.apk" — Telegram'da tarqatilgan yuklovchi, server: asdzxcqwe.icu
        "cac75f4531eddb1984650d9a88c91bd79560331bfed0de30afee62dde432840f" to "Android.Trojan.Dropper.ToydanArxivi",
    )

    private val KNOWN_BAD_PACKAGES = mapOf(
        "net.champion.golden" to "Android.Trojan.Dropper.ToydanArxivi",
    )

    fun signatureByHash(sha256: String): Finding? =
        KNOWN_BAD_SHA256[sha256.lowercase()]?.let { Finding(20, "Imzolar bazasida bor: $it", Tag.SIGNATURE, it) }

    fun signatureByPackage(pkg: String): Finding? =
        KNOWN_BAD_PACKAGES[pkg]?.let { Finding(20, "Ma'lum virus paketi: $pkg", Tag.SIGNATURE, it) }

    val TRUSTED_INSTALLERS = setOf(
        "com.android.vending",
        "com.google.android.feedback",
        "com.sec.android.app.samsungapps",
        "com.huawei.appmarket",
        "com.xiaomi.market",
        "com.xiaomi.mipicks",
        "com.xiaomi.discover",
        "com.facebook.system",
        "com.heytap.market",
        "com.oppo.market",
        "com.bbk.appstore",
        "com.vivo.appstore",
        "com.amazon.venezia",
        "ru.vk.store",
    )

    val MESSENGERS = setOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.instagram.android",
        "com.facebook.orca",
        "com.viber.voip",
        "com.imo.android.imoim",
    )

    private class PermRule(val perm: String, val points: Int, val text: String, val tag: Tag)

    private val PERMS = listOf(
        PermRule("android.permission.RECEIVE_SMS", 3, "Kelayotgan SMS'larni ushlab olish", Tag.SMS),
        PermRule("android.permission.READ_SMS", 3, "SMS xabarlarni o'qish", Tag.SMS),
        PermRule("android.permission.SEND_SMS", 2, "Yashirin SMS yuborish", Tag.SMS),
        PermRule("android.permission.RECEIVE_MMS", 1, "MMS xabarlarni ushlab olish", Tag.PERM),
        PermRule("android.permission.READ_CALL_LOG", 1, "Qo'ng'iroqlar tarixini o'qish", Tag.PERM),
        PermRule("android.permission.PROCESS_OUTGOING_CALLS", 1, "Chiquvchi qo'ng'iroqlarni kuzatish", Tag.PERM),
        PermRule("android.permission.READ_CONTACTS", 1, "Kontaktlarni o'qish", Tag.PERM),
        PermRule("android.permission.RECORD_AUDIO", 1, "Mikrofondan yozib olish", Tag.PERM),
        PermRule("android.permission.SYSTEM_ALERT_WINDOW", 1, "Boshqa ilovalar ustidan oyna chiqarish", Tag.PERM),
        PermRule("android.permission.REQUEST_INSTALL_PACKAGES", 1, "Boshqa APK'larni o'rnatish", Tag.INSTALLER),
        PermRule("android.permission.QUERY_ALL_PACKAGES", 1, "Barcha ilovalarni (banklarni) kuzatish", Tag.PERM),
        PermRule("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", 1, "Fonda to'xtovsiz ishlash", Tag.PERM),
    )

    /** Mashhur brend nomi -> uning rasmiy paketlari. */
    private val BRANDS = listOf(
        Regex("telegram|телеграм") to setOf("org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.messenger.beta", "org.thunderdog.challegram"),
        Regex("whatsapp|ватсап") to setOf("com.whatsapp", "com.whatsapp.w4b"),
        Regex("instagram|инстаграм") to setOf("com.instagram.android", "com.instagram.lite"),
        Regex("play market|play store|google play|плей маркет") to setOf("com.android.vending"),
        Regex("youtube|ютуб") to setOf("com.google.android.youtube", "com.google.android.apps.youtube.music", "com.google.android.apps.youtube.kids"),
        Regex("chrome") to setOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev"),
        Regex("payme") to setOf("uz.dida.payme"),
        Regex("\\bclick\\b") to setOf("air.com.ssdsoftwaresolutions.clickuz"),
        Regex("sberbank|сбербанк|сбер") to setOf("ru.sberbankmobile"),
        Regex("kaspi") to setOf("kz.kaspi.mobile"),
    )

    /** Firibgarlar APK'ga beradigan odatiy "qiziqtiruvchi" nomlar. */
    private val LURE = Regex(
        "to.?ydan|to.?yga|arxiv|архив|archive|свадьб|wedding|taklifnoma|приглаш|invitation|" +
            "foto|photo|rasm|hujjat|document|\\bpdf\\b|yangilan|update|обновлен|фото|видео|подарок|sovg.?a|yutuq|bonus|prize|pochta|почта|посылк|delivery|kuryer|курьер|to.?lov|оплат|kompensatsiya|компенсац",
    )

    /** Ilova nomi fayl kengaytmasi bilan tugaydi: "To'ydan arxivi(7).jpeg". */
    private val FILE_LIKE_LABEL = Regex(""".*\.(jpe?g|png|gif|pdf|docx?|xlsx?|txt|mp4|mp3|zip|rar)\s*$""")

    private val DOUBLE_EXT = Regex(".*\\.(jpe?g|png|gif|pdf|docx?|xlsx?|txt|mp4|mp3|zip)\\.apk$")

    fun permissionFindings(info: PackageInfo): List<Finding> {
        val requested = info.requestedPermissions?.toSet() ?: return emptyList()
        return PERMS.filter { it.perm in requested }.map { Finding(it.points, it.text, it.tag) }
    }

    fun componentFindings(info: PackageInfo): List<Finding> {
        val out = mutableListOf<Finding>()
        val services = info.services.orEmpty()
        if (services.any { it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" }) {
            out += Finding(3, "Accessibility xizmati (ekranni boshqarish)", Tag.ACCESSIBILITY)
        }
        if (services.any { it.permission == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" }) {
            out += Finding(2, "Bildirishnomalarni o'qish (SMS-kodlar)", Tag.NOTIF)
        }
        if (services.any { it.permission == "android.permission.BIND_VPN_SERVICE" }) {
            out += Finding(2, "O'z VPN xizmati (internet trafigini ushlash)", Tag.VPN)
        }
        if (info.receivers.orEmpty().any { it.permission == "android.permission.BIND_DEVICE_ADMIN" }) {
            out += Finding(2, "Qurilma administratori huquqini so'raydi", Tag.ADMIN)
        }
        return out
    }

    fun impersonation(label: String, pkg: String): Finding? {
        val l = label.lowercase()
        for ((pattern, official) in BRANDS) {
            val m = pattern.find(l) ?: continue
            if (pkg !in official) {
                return Finding(4, "Soxta \"${m.value}\" (rasmiy paket emas)", Tag.FAKE, m.value)
            }
        }
        return null
    }

    fun lureName(name: String): Finding? {
        val m = LURE.find(name.lowercase()) ?: return null
        return Finding(3, "Aldamchi nom: \"$name\"", Tag.LURE, m.value)
    }

    fun fileLikeLabel(label: String): Finding? =
        if (FILE_LIKE_LABEL.matches(label.lowercase())) Finding(4, "Ilova o'zini fayl qilib ko'rsatadi: \"$label\"", Tag.FAKE_FILE) else null

    fun doubleExtension(fileName: String): Finding? =
        if (DOUBLE_EXT.matches(fileName.lowercase())) Finding(4, "Yashirin kengaytma: $fileName", Tag.DOUBLE_EXT) else null

    fun verdict(kind: ThreatKind, target: String, name: String, findings: List<Finding>): Threat? {
        if (findings.isEmpty()) return null
        val score = findings.sumOf { it.points }
        val tags = findings.map { it.tag }.toSet()
        val banker = Tag.SMS in tags &&
            (Tag.ACCESSIBILITY in tags || Tag.HIDDEN in tags || Tag.ADMIN in tags || Tag.NOTIF in tags)

        // O'zini rasm/hujjat qilib ko'rsatib, yana boshqa APK o'rnatmoqchi — klassik yuklovchi troyan.
        val dropper = Tag.INSTALLER in tags && (Tag.FAKE_FILE in tags || Tag.DOUBLE_EXT in tags || Tag.LURE in tags)

        val level = when {
            Tag.SIGNATURE in tags || Tag.EICAR in tags || banker || dropper || score >= MALWARE_SCORE -> ThreatLevel.MALWARE
            score >= SUSPICIOUS_SCORE -> ThreatLevel.SUSPICIOUS
            else -> return null
        }

        val fakeBrand = findings.firstOrNull { it.tag == Tag.FAKE }?.meta
        val signature = findings.firstOrNull { it.tag == Tag.SIGNATURE }?.meta
        val family = when {
            signature != null -> signature
            Tag.EICAR in tags -> "EICAR-Test-File"
            banker -> "Android.Trojan.Banker.SmsStealer"
            dropper -> "Android.Trojan.Dropper"
            fakeBrand != null -> "Android.Trojan.Fake" + fakeBrand.replaceFirstChar { it.uppercase() }.replace(" ", "")
            Tag.ACCESSIBILITY in tags -> "Android.Spy.AccessibilityAbuse"
            Tag.SMS in tags -> "Android.Trojan.SmsSpy"
            Tag.ADMIN in tags -> "Android.Locker.DeviceAdmin"
            Tag.DOUBLE_EXT in tags -> "Android.Dropper.DisguisedApk"
            Tag.HIDDEN in tags -> "Android.Trojan.HiddenApp"
            level == ThreatLevel.SUSPICIOUS -> "Android.Riskware.Generic"
            else -> "Android.Malware.Generic"
        }

        return Threat(
            kind = kind,
            target = target,
            name = name,
            family = family,
            level = level,
            score = score,
            reasons = findings.sortedByDescending { it.points }.map { "${it.text} (+${it.points})" },
            tags = tags + listOfNotNull(if (banker) Tag.BANKER else null, if (dropper) Tag.DROPPER else null),
            brand = fakeBrand,
        )
    }
}
