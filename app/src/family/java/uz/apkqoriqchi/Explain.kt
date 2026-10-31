package uz.apkqoriqchi

/** Topilmalarni oddiy odam tushunadigan tilga o'giradi — texnik so'zlarsiz va qo'rqitmasdan. */
object Explain {

    fun levelLabel(t: Threat) = if (t.level == ThreatLevel.MALWARE) "Xavfli" else "Shubhali"

    fun kindLabel(t: Threat) = if (t.kind == ThreatKind.APP) "Ilova" else "Fayl: " + t.target.substringAfterLast('/')

    /** 1–3 ta qisqa jumla, eng muhimi birinchi. */
    fun reasons(t: Threat): List<String> {
        val tags = t.tags
        if (Tag.EICAR in tags) return listOf("Bu antivirus sinov fayli. U zararsiz, lekin o'chirib qo'yish mumkin.")

        val out = mutableListOf<String>()
        if (Tag.SIGNATURE in tags) out += "Bu aniq ma'lum virus — Telegram orqali odamlarni aldash uchun tarqatilgan."
        if (Tag.DROPPER in tags) out += "Bu yashirin yuklovchi: o'rnatsangiz, telefonga boshqa, yanada xavfli virusni tushiradi."
        if (Tag.EXFIL in tags) out += "Telefoningizdagi ma'lumotlarni firibgarlarning Telegram-botiga jo'natadi."
        if (Tag.PAYLOAD in tags && Tag.DROPPER !in tags) out += "Ichida boshqa, yashirib qo'yilgan dastur bor."
        if (Tag.EVASION in tags) out += "Antivirusdan yashirinish uchun ichki tuzilishi atayin chalkashtirilgan."
        if (Tag.FAKE_FILE in tags) out += "Rasm yoki hujjatga o'xshab ko'rinadi, aslida esa bu ilova."
        if (Tag.BANKER in tags) out += "SMS-kodlaringizni o'g'irlab, bank kartangizdan pul yechishga urinishi mumkin."
        if (Tag.FAKE in tags) out += "O'zini \"${t.brand?.replaceFirstChar { it.uppercase() }}\" qilib ko'rsatadi, lekin bu haqiqiy ilova emas."
        if (Tag.ACCESSIBILITY in tags && Tag.BANKER !in tags) out += "Ekraningizni ko'rishi va telefonni sizning o'rningizga boshqarishi mumkin."
        if (Tag.SMS in tags && Tag.BANKER !in tags) out += "SMS xabarlaringizni o'qishi yoki sizdan bexabar yuborishi mumkin."
        if (Tag.ADMIN in tags) out += "O'zini o'chirib bo'lmaydigan qilib qo'yishga urinadi."
        if (Tag.HIDDEN in tags) out += "Ikonkasini yashirib, sezdirmasdan ishlaydi."
        if (Tag.VPN in tags) out += "Internetdagi ma'lumotlaringizni o'z serveri orqali o'tkazishi mumkin."
        if (Tag.C2 in tags) out += "Shubhali internet manziliga ulanadi."
        if (Tag.PACKER in tags || Tag.DYNAMIC in tags) out += "Ichidagi kodini yashiradi — nima qilishini oldindan bilib bo'lmaydi."
        if (Tag.DOUBLE_EXT in tags && Tag.FAKE_FILE !in tags) out += "Rasm yoki hujjatga o'xshaydi, aslida esa ilova o'rnatuvchi fayl."
        if (Tag.LURE in tags) out += "Nomi sizni ochishga undash uchun atayin qiziqarli qilib qo'yilgan."
        if (Tag.MESSENGER in tags) out += "Telegram yoki boshqa messenjer orqali yuborilgan."
        if (Tag.APK in tags && out.isEmpty()) out += "Bu ilova o'rnatuvchi fayl. Kim yuborganini bilmasangiz, o'rnatmang."
        if (Tag.SIDELOAD in tags && out.isEmpty()) out += "Play Market'dan emas, boshqa joydan o'rnatilgan va ko'p ruxsat so'raydi."
        if (out.isEmpty()) out += "Xavfli ilovalarga xos belgilari bor."
        return out.take(3)
    }

    fun advice(t: Threat): String = when {
        t.level == ThreatLevel.MALWARE -> "O'chirishni maslahat beramiz."
        t.kind == ThreatKind.APP -> "Agar bu ilovani o'zingiz o'rnatgan bo'lsangiz va unga ishonsangiz, qoldirishingiz mumkin."
        else -> "Agar bu faylni kim yuborganini bilmasangiz, o'chirib tashlang."
    }

    class Tip(val title: String, val text: String, val settingsAction: String?)

    fun tip(issue: Scanner.Issue): Tip? = when (issue) {
        Scanner.Issue.NO_LOCK -> Tip(
            "Ekran qulfi qo'yilmagan",
            "Telefon yo'qolsa, begona odam uni bemalol ochishi mumkin. PIN-kod yoki barmoq izi qo'yishni maslahat beramiz.",
            android.provider.Settings.ACTION_SECURITY_SETTINGS,
        )
        Scanner.Issue.ADB -> Tip(
            "USB orqali boshqaruv yoqilgan",
            "Bu sozlama dasturchilar uchun. Agar siz dasturchi bo'lmasangiz, uni o'chirib qo'ygan ma'qul.",
            android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        )
        Scanner.Issue.ROOT -> Tip(
            "Telefonning ichki himoyasi o'chirilgan",
            "Bu telefonda maxsus (root) huquqlar ochilgan. Bank ilovalaridan foydalanishda ehtiyot bo'ling.",
            null,
        )
        Scanner.Issue.OLD_PATCH -> Tip(
            "Telefon uzoq vaqt yangilanmagan",
            "Sozlamalar → Telefon haqida → Tizim yangilanishi bo'limidan yangilanishlarni tekshiring.",
            android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS,
        )
        Scanner.Issue.DEV_MODE -> null
    }

    val HOME_TIPS = listOf(
        "Telegram'da notanish odam yuborgan \".apk\" fayllarni ochmang — ko'pincha bu virus bo'ladi.",
        "Tanishingiz \"rasmlarni ko'r\" deb fayl yuborsa, avval unga qo'ng'iroq qilib so'rang. Uning akkaunti buzilgan bo'lishi mumkin.",
        "SMS orqali kelgan kodni hech kimga aytmang — bank xodimi ham bunday kodni so'ramaydi.",
        "Ilovalarni faqat Play Market'dan o'rnating.",
        "Telegram'da ikki bosqichli tasdiqlash (bulutli parol)ni yoqing: Sozlamalar → Maxfiylik.",
    )
}
