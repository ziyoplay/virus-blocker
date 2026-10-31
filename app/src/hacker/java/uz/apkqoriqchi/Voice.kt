package uz.apkqoriqchi

/** Real-time bildirishnomalar matni — hacker uslubi. */
object Voice {
    const val CHANNEL_STATUS = "Real-time himoya holati"
    const val CHANNEL_ALERT = "MALWARE ogohlantirishlari"
    const val CHANNEL_INFO = "Skan natijalari"

    const val ONGOING_TITLE = "[ REAL-TIME HIMOYA: ON ]"
    const val ONGOING_TEXT = "watch --strict: har bir yangi ilova + butun xotiradagi yangi fayllar"
    const val CLEAN_TITLE = "[ OK ] tahdid topilmadi"
    const val REMOVE = "☠ O'CHIRISH"

    fun alertTitle(t: Threat): String {
        val tag = if (t.level == ThreatLevel.MALWARE) "☠ MALWARE" else "⚠ SHUBHALI"
        val where = if (t.kind == ThreatKind.APP) "ilova" else "fayl"
        val name = if (t.kind == ThreatKind.FILE) t.target.substringAfterLast('/') else t.name
        return "$tag [$where]: $name"
    }

    fun alertText(t: Threat): String = buildString {
        append("${t.family} · xavf ${t.score}\n")
        append(t.target).append('\n')
        t.reasons.take(4).forEach { append("› ").append(it).append('\n') }
    }.trimEnd()

    fun fileCleanText(name: String) = "scan: $name — toza"
    fun removedToast(name: String, cleaned: Int) =
        "[✓] YO'Q QILINDI: $name" + if (cleaned > 0) "\n[✓] qoldiqlar: $cleaned ta tozalandi" else ""

    fun autoRemovedTitle(t: Threat) = "[✓] AUTO-PURGE: ${t.target.substringAfterLast('/')}"
    fun autoRemovedText(t: Threat) = "${t.family} · xavf ${t.score}\nfayl shred qilindi: ${t.target}"
    fun notRemovedToast(name: String) = "[✗] o'chirilmadi: $name"
    fun keptToast(name: String) = "[+] whitelist: $name — endi tekshirilmaydi"

    const val KEEP = "QOLDIRISH"
    val DIALOG_THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert

    const val CLOSE = "YOPISH"
    const val MONO_LOG = true
    const val AUTO_REMOVING = "[*] auto-purge: messenjerdan kelgan virus — o'chirilmoqda..."

    /** Log ranglari terminaldagidek qoladi (oyna qora). */
    fun logColor(color: Int) = color

    fun checkingTitle(label: String) = "> scan --strict $label"

    fun checkTitle(label: String, t: Threat?) = when (t?.level) {
        null -> "[✓] VIRUS EMAS: $label"
        ThreatLevel.MALWARE -> "☠ VIRUS: $label"
        ThreatLevel.SUSPICIOUS -> "⚠ SHUBHALI: $label"
    }

    fun verdictText(label: String, t: Threat?) =
        if (t == null) "tahdid topilmadi — xavf chegarasidan past" else "${t.family} · xavf ${t.score}"

    const val SITE_BACK = "ORQAGA"
    const val SITE_OPEN = "BARIBIR OCHISH"
    const val SITE_OK = "OK"

    fun siteTitle(v: SiteVerdict, closed: Boolean) = when {
        closed -> "☠ XAVFLI SAYT BLOKLANDI"
        v.level == ThreatLevel.MALWARE -> "☠ XAVFLI SAYT"
        else -> "⚠ SHUBHALI SAYT"
    }

    fun siteText(v: SiteVerdict, closed: Boolean) = buildString {
        append("host: ${v.host}\nxavf: ${v.score}\n\n")
        v.reasons.forEach { append("› ").append(it).append('\n') }
        if (closed) append("\n[✓] sahifa yopildi — orqaga qaytarildi")
    }.trimEnd()
}
