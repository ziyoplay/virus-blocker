package uz.apkqoriqchi

/** Real-time bildirishnomalar matni — oddiy, tushunarli til. */
object Voice {
    const val CHANNEL_STATUS = "Doimiy himoya"
    const val CHANNEL_ALERT = "Xavf haqida ogohlantirish"
    const val CHANNEL_INFO = "Tekshiruv natijalari"

    const val ONGOING_TITLE = "Telefon himoyada"
    const val ONGOING_TEXT = "Har bir yangi ilova va fayl avtomatik tekshiriladi"
    const val CLEAN_TITLE = "Tekshirildi — xavfsiz ✓"
    const val REMOVE = "O'chirish"

    fun alertTitle(t: Threat): String {
        val danger = t.level == ThreatLevel.MALWARE
        return when (t.kind) {
            ThreatKind.FILE -> if (danger) "Xavfli fayl keldi!" else "Shubhali fayl keldi"
            ThreatKind.APP -> if (danger) "Xavfli ilova o'rnatildi!" else "Shubhali ilova o'rnatildi"
        }
    }

    fun alertText(t: Threat): String {
        val name = if (t.kind == ThreatKind.FILE) t.target.substringAfterLast('/') else t.name
        val advice = if (t.kind == ThreatKind.FILE) "Uni ochmang va o'rnatmang — \"O'chirish\"ni bosing." else "\"O'chirish\"ni bosishni maslahat beramiz."
        return "\"$name\": ${Explain.reasons(t).first()} $advice"
    }

    fun appCleanText(label: String) = "Yangi ilova \"$label\" tekshirildi. Xavf topilmadi."
    fun fileCleanText(name: String) = "\"$name\" fayli tekshirildi. Xavf topilmadi."
    fun removedToast(name: String) = "O'chirildi: $name"
    fun notRemovedToast(name: String) = "O'chirilmadi: $name. Qayta urinib ko'ring."
}
