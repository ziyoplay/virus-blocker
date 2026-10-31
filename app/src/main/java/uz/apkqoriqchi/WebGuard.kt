package uz.apkqoriqchi

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.net.IDN

class SiteVerdict(val url: String, val host: String, val level: ThreatLevel, val score: Int, val reasons: List<String>)

/**
 * Web himoya: brauzer manzil satridagi sayt tekshiriladi — avval xavfli saytlar bazasi ([WebDb]),
 * so'ng evristika (soxta bank/to'lov saytlari, IP-manzil, punycode, shubhali zonalar, APK yuklash).
 * Manzil satridan boshqa hech narsa o'qilmaydi, saytlar hech qayerga yuborilmaydi.
 */
object WebGuard {
    const val DANGER_SCORE = 8
    const val SUSPICIOUS_SCORE = 5

    /** Bitta saytdan ketma-ket (redirect zanjiri) ogohlantirish chiqmasligi uchun. */
    private const val ALERT_COOLDOWN_MS = 3_000L
    private const val ALERT_QUIET_MS = 1_500L

    /** O'zimiz tahlil qilgan namunalardagi serverlar. */
    private val KNOWN_BAD_HOSTS = mapOf(
        "asdzxcqwe.icu" to "\"To'ydan arxivi\" troyanining serveri",
    )

    /**
     * Brend nomi -> rasmiy domenlari. [context] — oddiy so'z ham bo'lishi mumkin bo'lgan nomlar
     * ("click", "uzum"): ular faqat yonida "uz", "pay", "bank" yoki aldamchi so'z bo'lsa hisoblanadi.
     */
    private class Brand(val name: String, val tokens: Set<String>, val official: Set<String>, val context: Boolean = false)

    private val BRANDS = listOf(
        Brand("Payme", setOf("payme", "paycom"), setOf("payme.uz", "paycom.uz")),
        Brand("Click", setOf("click"), setOf("click.uz"), context = true),
        Brand("Uzcard", setOf("uzcard"), setOf("uzcard.uz")),
        Brand("Humo", setOf("humo", "humocard"), setOf("humocard.uz", "humo.uz"), context = true),
        Brand("Uzum", setOf("uzum", "uzumbank", "apelsin"), setOf("uzum.uz", "uzumbank.uz", "uzum.com", "apelsin.uz"), context = true),
        Brand("Paynet", setOf("paynet"), setOf("paynet.uz")),
        Brand("Kapitalbank", setOf("kapitalbank"), setOf("kapitalbank.uz")),
        Brand("Hamkorbank", setOf("hamkorbank"), setOf("hamkorbank.uz")),
        Brand("Ipak Yo'li", setOf("ipakyulibank", "ipakyuli"), setOf("ipakyulibank.uz")),
        Brand("Agrobank", setOf("agrobank"), setOf("agrobank.uz")),
        Brand("Xalq banki", setOf("xalqbank", "xalqbanki"), setOf("xb.uz", "xalqbank.uz")),
        Brand("Anorbank", setOf("anorbank"), setOf("anorbank.uz")),
        Brand("Asakabank", setOf("asakabank"), setOf("asakabank.uz")),
        Brand("TBC Bank", setOf("tbcbank"), setOf("tbcbank.uz", "tbcbank.ge")),
        Brand("my.gov.uz", setOf("mygov"), setOf("my.gov.uz")),
        Brand("Soliq", setOf("soliq"), setOf("soliq.uz")),
        Brand("O'zbekiston pochtasi", setOf("pochta", "uzpost"), setOf("pochta.uz"), context = true),
        Brand("OLX", setOf("olx"), setOf("olx.uz", "olx.kz", "olx.ua", "olx.pl", "olx.ro", "olx.pt", "olx.bg", "olx.in", "olx.com", "olx.com.br")),
        Brand("Telegram", setOf("telegram"), setOf("telegram.org", "telegram.me", "t.me", "telegra.ph", "telesco.pe")),
        Brand("WhatsApp", setOf("whatsapp"), setOf("whatsapp.com", "whatsapp.net", "wa.me")),
        Brand("Instagram", setOf("instagram"), setOf("instagram.com")),
        Brand("Facebook", setOf("facebook"), setOf("facebook.com", "fb.com", "messenger.com")),
        Brand("Steam", setOf("steamcommunity", "steampowered"), setOf("steamcommunity.com", "steampowered.com")),
        Brand("Sberbank", setOf("sberbank", "sber"), setOf("sberbank.ru", "sber.ru", "sberbank.com")),
        Brand("Kaspi", setOf("kaspi"), setOf("kaspi.kz")),
        Brand("Tinkoff", setOf("tinkoff"), setOf("tinkoff.ru", "tbank.ru")),
        Brand("Apple", setOf("icloud", "appleid"), setOf("icloud.com", "apple.com")),
    )

    /** "paymeuz", "mypayme" kabi yopishtirilgan qismlar ham brend deb sanaladi. */
    private val GLUED = setOf("uz", "uzb", "app", "pay", "bonus", "login", "online", "id", "gift", "karta", "card", "auth", "verify", "secure", "web", "my")
    private val CONTEXT_TOKENS = setOf("uz", "uzb", "pay", "bank", "karta", "card")

    private val LURE = Regex(
        "bonus|yutuq|sovg.?a|prize|gift|free|login|signin|verif|kompensat|to.?lov|karta|card|secure|account|update|" +
            "konkurs|aksiya|viplat|keshbek|cashback|giveaway|выплат|компенсац|приз|подар|бонус|розыгр",
    )

    private val SUSPICIOUS_TLDS = setOf(
        "icu", "top", "xyz", "tk", "ml", "ga", "cf", "gq", "buzz", "rest", "cyou", "monster", "sbs", "cfd", "lol", "quest", "bond", "click",
    )

    /** Bepul hosting va tunnellar: firibgarlar sayt ochishga eng ko'p shulardan foydalanadi. */
    private val FREE_HOSTING = listOf(
        "pages.dev", "workers.dev", "vercel.app", "netlify.app", "glitch.me", "web.app", "firebaseapp.com", "000webhostapp.com",
        "weebly.com", "wixsite.com", "github.io", "replit.dev", "onrender.com", "ngrok-free.app", "ngrok.io", "ngrok.app",
        "trycloudflare.com", "loca.lt", "serveo.net", "tilda.ws", "blogspot.com",
    )

    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    private val PRIVATE_IP = Regex("""(10|127)\..*|192\.168\..*|172\.(1[6-9]|2\d|3[01])\..*""")
    private val HOST_CHARS = Regex("[a-z0-9.-]+")

    // --- holat ---

    fun isOn(ctx: Context) = Prefs(ctx).webGuard

    fun enable(ctx: Context) {
        Prefs(ctx).webGuard = true
        WebDbJob.schedule(ctx, now = !WebDb.exists(ctx))
        AutoConfirmService.instance?.refreshScope()
    }

    fun disable(ctx: Context) {
        Prefs(ctx).webGuard = false
        WebDbJob.cancel(ctx)
        AutoConfirmService.instance?.refreshScope()
    }

    /** Ilova ochilganda: baza kunlik yangilanib turganiga ishonch hosil qilamiz. */
    fun resume(ctx: Context) {
        if (isOn(ctx)) WebDbJob.schedule(ctx, now = !WebDb.exists(ctx))
    }

    // --- tekshiruv ---

    fun check(ctx: Context, raw: String): SiteVerdict? = check(raw) { WebDb.lookup(ctx, it) }

    /** [lookup] — xavfli saytlar bazasi (testlarda o'rniga soxtasi beriladi). */
    internal fun check(raw: String, lookup: (String) -> WebDb.Kind?): SiteVerdict? {
        val host = hostOf(raw) ?: return null
        val url = raw.trim()
        val lower = url.lowercase()
        val found = mutableListOf<Pair<Int, String>>()

        KNOWN_BAD_HOSTS[host]?.let { found += 20 to "Imzolar bazasida bor: $it" }
        when (lookup(host)) {
            WebDb.Kind.PHISHING -> found += 20 to "Firibgar (phishing) saytlar bazasida bor"
            WebDb.Kind.MALWARE -> found += 20 to "Virus tarqatuvchi saytlar bazasida bor"
            null -> {}
        }

        val labels = host.split('.')
        val tokens = labels.dropLast(1).flatMap { it.split('-') }.filter { it.isNotEmpty() }
        val lure = LURE.find(lower)
        brandOf(host, tokens, lure != null)?.let {
            found += 7 to "O'zini \"${it.name}\" qilib ko'rsatadi, lekin rasmiy sayti emas (rasmiysi: ${it.official.first()})"
        }
        if (lure != null) found += 2 to "Manzilda aldamchi so'z: \"${lure.value}\""
        if (IPV4.matches(host) && !PRIVATE_IP.matches(host)) found += 4 to "Sayt nomi o'rniga faqat IP-manzil"
        if (labels.any { it.startsWith("xn--") }) found += 4 to "Harflari almashtirilgan manzil (o'xshash harflar bilan aldash)"
        if (labels.last() in SUSPICIOUS_TLDS) found += 2 to "Firibgarlar ko'p ishlatadigan domen zonasi: .${labels.last()}"
        FREE_HOSTING.firstOrNull { host.endsWith(".$it") }?.let { found += 1 to "Bepul hostingda joylashgan: $it" }
        if ('@' in lower.substringAfter("://").substringBefore('/')) found += 4 to "Manzil ichida yashirin nom (@) bor"
        if (host.count { it == '-' } >= 4 || labels.size >= 6) found += 1 to "G'alati uzun manzil"
        val path = lower.substringAfter(host, "").substringBefore('?').substringBefore('#')
        if (path.endsWith(".apk")) found += 3 to "Sayt telefonga APK fayl (ilova) yuklatmoqchi"

        val score = found.sumOf { it.first }
        val level = when {
            score >= DANGER_SCORE -> ThreatLevel.MALWARE
            score >= SUSPICIOUS_SCORE -> ThreatLevel.SUSPICIOUS
            else -> return null
        }
        return SiteVerdict(url, host, level, score, found.sortedByDescending { it.first }.map { it.second })
    }

    private fun brandOf(host: String, tokens: List<String>, lure: Boolean): Brand? {
        for (b in BRANDS) {
            if (b.official.any { host == it || host.endsWith(".$it") }) continue
            val hit = tokens.any { t ->
                b.tokens.any { brand ->
                    t == brand || (t.startsWith(brand) && t.removePrefix(brand) in GLUED) || (t.endsWith(brand) && t.removeSuffix(brand) in GLUED)
                }
            }
            if (!hit) continue
            if (b.context && !lure && tokens.none { it in CONTEXT_TOKENS } && tokens.none { t -> b.tokens.any { t != it && t.startsWith(it) } }) continue
            return b
        }
        return null
    }

    /** Manzil satridagi matndan sayt nomini ajratadi; qidiruv so'zi bo'lsa — null. */
    fun hostOf(raw: String): String? {
        val s = raw.trim().lowercase()
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        val authority = s.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':').trimEnd('.')
        if ('.' !in host) return null
        val ascii = try {
            IDN.toASCII(host)
        } catch (e: Exception) {
            return null
        }.lowercase()
        if (!HOST_CHARS.matches(ascii)) return null
        return ascii.removePrefix("www.")
    }

    // --- brauzer ---

    /** Brauzer -> manzil satrining view id'si. null — id noma'lum, umumiy qidiruv bilan topiladi. */
    private val URL_BAR_IDS = mapOf(
        "com.android.chrome" to "url_bar",
        "com.chrome.beta" to "url_bar",
        "com.chrome.dev" to "url_bar",
        "com.chrome.canary" to "url_bar",
        "org.chromium.chrome" to "url_bar",
        "com.brave.browser" to "url_bar",
        "com.kiwibrowser.browser" to "url_bar",
        "com.microsoft.emmx" to "url_bar",
        "com.vivaldi.browser" to "url_bar",
        "org.mozilla.firefox" to "mozac_browser_toolbar_url_view",
        "org.mozilla.firefox_beta" to "mozac_browser_toolbar_url_view",
        "org.mozilla.fenix" to "mozac_browser_toolbar_url_view",
        "com.sec.android.app.sbrowser" to "location_bar_edit_text",
        "com.opera.browser" to "url_field",
        "com.opera.mini.native" to "url_field",
        "com.duckduckgo.mobile.android" to "omnibarTextInput",
        "com.yandex.browser" to null,
        "com.mi.globalbrowser" to null,
        "com.android.browser" to null,
        "com.UCMobile.intl" to null,
        "com.huawei.browser" to null,
        "com.heytap.browser" to null,
    )
    private val URL_ID_HINT = Regex("url|address|omnibox|omnibar|location", RegexOption.IGNORE_CASE)
    private const val MAX_NODES = 250

    /** Telefondagi barcha brauzerlar: ma'lumlari + http havolani ocha oladigan ilovalar. */
    fun browserPackages(ctx: Context): Set<String> {
        val resolved = try {
            ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com")), PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }
        } catch (e: Exception) {
            emptyList()
        }
        return (URL_BAR_IDS.keys + resolved).filter { it != ctx.packageName }.toSet()
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastUrl: String? = null
    private var alertedHost: String? = null
    private var alertedAt = 0L
    private var quietUntil = 0L

    /** Foydalanuvchi "baribir ochish" degan saytlar (ilova yopilguncha). */
    private val allowed = HashSet<String>()

    fun allow(host: String) {
        allowed += host
    }

    /** Brauzer oynasi o'zgarganda [AutoConfirmService] chaqiradi. */
    fun inspect(service: AccessibilityService, pkg: String) {
        val root = service.rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val now = SystemClock.uptimeMillis()
        // Ogohlantirish endi ochilmoqda: sahifa yopilgach brauzer boshqa tabga o'tadi — u ogohlantirish
        // yopilgandan keyin tekshiriladi, aks holda muhimroq oyna ustidan almashib ketadi.
        if (now < quietUntil || SiteBlockActivity.showing) return
        val raw = readUrl(root, pkg) ?: return
        if (raw == lastUrl) return
        lastUrl = raw
        val v = check(service, raw) ?: return
        if (v.host in allowed) return
        if (v.host == alertedHost && now - alertedAt < ALERT_COOLDOWN_MS) return
        alertedHost = v.host
        alertedAt = now
        quietUntil = now + ALERT_QUIET_MS

        val prefs = Prefs(service)
        prefs.sitesBlocked = prefs.sitesBlocked + 1
        // Aniq xavfli sayt darhol yopiladi (orqaga), so'ng nima bo'lgani tushuntiriladi.
        val close = v.level == ThreatLevel.MALWARE
        if (close) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        handler.postDelayed({
            try {
                service.startActivity(SiteBlockActivity.intent(service, v, pkg, closed = close))
            } catch (e: Exception) {
            }
        }, if (close) 300 else 0)
    }

    /** Ogohlantirish oynasi yopilgach brauzerda bir sahifa orqaga qaytadi. */
    fun goBack() {
        val service = AutoConfirmService.instance ?: return
        handler.postDelayed({ service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }, 300)
    }

    private fun readUrl(root: AccessibilityNodeInfo, pkg: String): String? {
        URL_BAR_IDS[pkg]?.let { id ->
            val nodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
            // Foydalanuvchi manzil yozayotgan bo'lsa (fokusda) — chala matnni tekshirmaymiz.
            if (nodes.isNotEmpty()) return nodes.firstOrNull { !it.isFocused && !it.text.isNullOrBlank() }?.text?.toString()
        }
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var seen = 0
        while (stack.isNotEmpty() && seen++ < MAX_NODES) {
            val n = stack.removeLast()
            val id = n.viewIdResourceName
            val text = n.text?.toString()
            if (id != null && URL_ID_HINT.containsMatchIn(id.substringAfter(":id/")) && !n.isFocused && text != null && hostOf(text) != null) {
                return text
            }
            for (i in n.childCount - 1 downTo 0) n.getChild(i)?.let { stack.addLast(it) }
        }
        return null
    }
}
