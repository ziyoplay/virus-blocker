# APK Qo'riqchi / Telefon Qo'riqchi

Android uchun antivirus. Telegram orqali tarqatiladigan soxta "rasm", "hujjat" va "to'y arxivi" APK'larini, SMS va bank troyanlarini topadi va o'chiradi.

Bitta dvigatel, ikki xil ko'rinish:

| | **APK Qo'riqchi** (hacker) | **Telefon Qo'riqchi** (oila) |
|---|---|---|
| Kim uchun | O'zim, do'stlar, texnikani tushunadiganlar | Ota-ona, oila a'zolari |
| Ko'rinishi | Qora terminal, yashil/qizil loglar | Oq, sokin, katta tugmalar |
| Tili | `MALWARE`, `Android.Trojan.Dropper`, ballar | "Xavfli fayl keldi! Uni ochmang" |
| Paket | `uz.apkqoriqchi` | `uz.apkqoriqchi.family` |

Ikkalasini bitta telefonga yonma-yon o'rnatish mumkin.

## Yuklab olish

[**Releases**](../../releases) bo'limidan kerakli APK'ni oling:

- `APK_Qoriqchi_hacker.apk` — hacker versiyasi
- `Telefon_Qoriqchi_oila.apk` — oila versiyasi

Android 8.0 va undan yangi versiyalarda ishlaydi.

## Nima qiladi

**To'liq skanerlash**
- O'rnatilgan har bir ilovani tekshiradi.
- Telefon xotirasidagi APK fayllarni va APK'ga aylantirilgan fayllarni topadi (masalan, `rasm.jpg` niqobidagi ilova).
- Telefon sozlamalarini tekshiradi: USB debugging, ekran qulfi, root, eskirgan xavfsizlik yangilanishi.

**Real-time himoya** (fonda ishlaydi)
- Har bir yangi o'rnatilgan ilovani o'rnatilishi bilan tekshiradi — siz uni ochishdan oldin.
- Xotiraning istalgan joyiga tushgan har bir yangi faylni tekshiradi (Telegram, WhatsApp, Download, Bluetooth va boshqalar).
- Xavf topilsa, **O'chirish** tugmali bildirishnoma chiqaradi.

**O'chirish**
- Ilovalar Android'ning tizim oynasi orqali o'chiriladi: har biri uchun tasdiq so'raladi.
- Fayllar nol bilan qayta yozilib, keyin o'chiriladi.
- O'chirishdan oldin ro'yxat chiqadi, nimani o'chirishni o'zingiz tanlaysiz.

## Qanday aniqlaydi

1. **Imzolar bazasi.** Tasdiqlangan zararli namunalarning SHA-256 xeshi va paket nomi. Nomi o'zgartirilsa ham aniq topiladi.
2. **Evristika.** Har bir belgi ball qo'shadi: 8 va undan ko'p ball — `MALWARE`, 5–7 ball — `SHUBHALI`.

| Belgi | Misol |
|---|---|
| SMS o'qish/ushlash + Accessibility / yashirin ikonka | Bank troyani (avtomatik `MALWARE`) |
| Boshqa APK o'rnatish + o'zini fayl qilib ko'rsatish | Dropper (avtomatik `MALWARE`) |
| Ilova nomi `.jpeg`, `.pdf` bilan tugaydi | `To'ydan arxivi(7).jpeg` |
| Yolg'on kengaytma | `hujjat.pdf.apk`, ichida APK bo'lgan `rasm.jpg` |
| Soxta brend | Telegram, Payme, Click, Play Market — lekin rasmiy paket emas |
| Aldamchi nom | "foto", "to'ydan", "arxiv", "taklifnoma", "sovg'a", "pochta" ... |
| O'z VPN xizmati, qurilma administratori, bildirishnomalarni o'qish | Josuslik belgilari |
| Play Market'dan emas, debug-kalit bilan imzolangan | Tekshirilmagan manba |

Rasmiy do'kondan (Play Market, Galaxy Store, Xiaomi GetApps va boshqalar) va telefon ishlab chiqaruvchisidan kelgan ilovalarga ruxsatlar bo'yicha ball qo'yilmaydi. Aks holda har bir messenjer "shubhali" bo'lib chiqardi.

## Cheklovlar

Antivirusning nima qila olmasligini bilib qo'ying:

- **O'rnatishni bloklay olmaydi.** Android buni hech bir antivirusga ruxsat bermaydi. Qo'riqchi ogohlantiradi, qaror sizda qoladi.
- **Siz ochgan virusni to'xtata olmaydi.** U internetda qilib ulgurgan ishni ham qaytara olmaydi. Ogohlantirish chiqsa — **ochmang**.
- **Telegram'ning yopiq keshini ko'ra olmaydi.** Telegram faylni o'zining yopiq papkasida saqlasa, ogohlantirish faylni o'rnatishni bosganingizda chiqadi. Baribir ochishdan oldin.
- **Evristika 100% emas.** Yangi, hali hech kim ko'rmagan virus o'tib ketishi mumkin.

## Xiaomi / Redmi / POCO egalari uchun

MIUI fondagi ilovalarni o'chirib qo'yadi. Himoya doim ishlashi uchun:

1. **Sozlamalar → Ilovalar → Ruxsatlar → Avtozapusk** → Qo'riqchi'ni yoqing.
2. **Ilova sozlamalari → Batareya → Cheklovsiz**.

## Tahlil qilingan namuna

`To`ydan arxivi(7).jpeg.apk` — Telegram'da buzilgan akkauntlar orqali tarqatilgan dropper.

| | |
|---|---|
| SHA-256 | `cac75f4531eddb1984650d9a88c91bd79560331bfed0de30afee62dde432840f` |
| Paket | `net.champion.golden` |
| Server | `asdzxcqwe.icu` (ochilganda `/webview/photo?buildVersion=asdsgsva01` sahifasini yuklaydi) |
| Xatti-harakati | O'zini to'y rasmlari arxivi qilib ko'rsatadi, 2-bosqich virusni o'rnatish huquqini so'raydi. Ichida shifrlangan yuk bor, VPN xizmati va yashirin "Play" ikonkasi bor, Xiaomi/Huawei/Vivo xavfsizlik ilovalarini qidiradi. |

Tahlil internetsiz Android emulyatorida o'tkazilgan.

## O'zingiz yig'ish

Kerak bo'ladi: JDK 17 va Android SDK (compileSdk 36).

```bash
./gradlew assembleHackerDebug      # hacker versiyasi
./gradlew assembleFamilyDebug      # oila versiyasi
./gradlew dist                     # ikkala release APK -> dist/
```

Release imzosi uchun loyiha ildizida `keystore.properties` fayli bo'lishi kerak (repoda yo'q):

```properties
storeFile=keystore/qoriqchi.jks
storePassword=...
keyAlias=qoriqchi
keyPassword=...
```

Bu fayl bo'lmasa, release debug-kalit bilan imzolanadi.

## Loyiha tuzilishi

```
app/src/main/     umumiy dvigatel: Rules (qoidalar, imzolar), Scanner, GuardService (real-time), Remover
app/src/hacker/   hacker interfeysi: terminal ekranlari, matnlar
app/src/family/   oila interfeysi: kartochkalar, oddiy tushuntirishlar (Explain)
```

Tashqi kutubxonalar yo'q — faqat Android SDK va Kotlin.
