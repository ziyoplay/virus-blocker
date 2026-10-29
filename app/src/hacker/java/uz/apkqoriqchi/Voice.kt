package uz.apkqoriqchi

/** Real-time bildirishnomalar matni — hacker uslubi. */
object Voice {
    const val CHANNEL_STATUS = "Real-time himoya holati"
    const val CHANNEL_ALERT = "MALWARE ogohlantirishlari"
    const val CHANNEL_INFO = "Skan natijalari"

    const val ONGOING_TITLE = "[ REAL-TIME HIMOYA: ON ]"
    const val ONGOING_TEXT = "watch: har bir yangi ilova + butun xotiradagi yangi fayllar"
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

    fun appCleanText(label: String) = "scan: $label — toza"
    fun fileCleanText(name: String) = "scan: $name — toza"
    fun removedToast(name: String) = "[✓] YO'Q QILINDI: $name"
    fun notRemovedToast(name: String) = "[✗] o'chirilmadi: $name"
}
