package uz.apkqoriqchi

/** Real-time bildirishnomalar matni — oddiy, tushunarli til. */
object Voice {
    const val CHANNEL_STATUS = "Doimiy himoya"
    const val CHANNEL_ALERT = "Xavf haqida ogohlantirish"
    const val CHANNEL_INFO = "Tekshiruv natijalari"

    const val ONGOING_TITLE = "Telefon himoyada"
    const val ONGOING_TEXT = "Har bir yangi ilova va fayl avtomatik tekshiriladi, xavflisi o'chiriladi"
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

    fun fileCleanText(name: String) = "\"$name\" fayli tekshirildi. Xavf topilmadi."
    fun removedToast(name: String, cleaned: Int) =
        "O'chirildi: $name" + if (cleaned > 0) "\nUndan qolgan $cleaned ta fayl ham tozalandi." else ""
    fun keptToast(name: String) = "\"$name\" qoldirildi. Endi u tekshirilmaydi."
    fun notRemovedToast(name: String) = "O'chirilmadi: $name. Qayta urinib ko'ring."

    fun autoRemovedTitle(t: Threat) = "Xavfli fayl o'chirildi ✓"
    fun autoRemovedText(t: Threat) =
        "\"${t.target.substringAfterLast('/')}\" virus edi. Biz uni siz ochmasdan oldin o'chirib tashladik — hech narsa qilishingiz shart emas."

    const val KEEP = "Qoldirish"
    val DIALOG_THEME = android.R.style.Theme_DeviceDefault_Light_Dialog_Alert

    const val CLOSE = "Yopish"
    const val MONO_LOG = false
    const val AUTO_REMOVING = "Bu virus messenjer orqali kelgan — avtomatik o'chirilmoqda..."

    /** Oq oynada terminal ranglari ko'rinmaydi — ularni yumshoqroqqa almashtiramiz. */
    fun logColor(color: Int) = when (color) {
        Palette.RED, Palette.RED_DIM -> 0xFFC62828.toInt()
        Palette.YELLOW, Palette.YELLOW_DIM -> 0xFFB26A00.toInt()
        Palette.GREEN -> 0xFF2E7D32.toInt()
        else -> 0xFF555555.toInt()
    }

    fun checkingTitle(label: String) = "\"$label\" tekshirilmoqda..."

    fun checkTitle(label: String, t: Threat?) = when (t?.level) {
        null -> "Virus emas ✓"
        ThreatLevel.MALWARE -> "Bu virus!"
        ThreatLevel.SUSPICIOUS -> "Shubhali ilova"
    }

    fun verdictText(label: String, t: Threat?): String =
        if (t == null) {
            "\"$label\" tekshirildi — xavfli narsa topilmadi. Pastda nimalar tekshirilgani yozilgan."
        } else {
            "\"$label\": " + Explain.reasons(t).joinToString(" ") + " " + Explain.advice(t)
        }

    const val SITE_BACK = "Orqaga qaytish"
    const val SITE_OPEN = "Baribir ochish"
    const val SITE_OK = "Tushunarli"

    fun siteTitle(v: SiteVerdict, closed: Boolean) = when {
        closed -> "Xavfli sayt yopildi!"
        v.level == ThreatLevel.MALWARE -> "Bu sayt xavfli!"
        else -> "Bu sayt shubhali"
    }

    fun siteText(v: SiteVerdict, closed: Boolean) =
        "\"${v.host}\" sayti " +
            (if (closed) "firibgarlarniki bo'lishi mumkin, shuning uchun uni yopdik." else "xavfli bo'lishi mumkin.") +
            "\n\nSabab:\n• " + v.reasons.joinToString("\n• ") +
            "\n\nBunday saytga karta raqami, SMS-kod yoki parolingizni hech qachon kiritmang."
}
