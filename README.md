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
- Har bir yangi o'rnatilgan ilovani o'rnatilishi bilan **qattiq rejimda** tekshiradi — siz uni ochishdan oldin. Qattiq rejimda APK ichi ochib ko'riladi (pastdagi "Chuqur tahlil"), "shubhali" chegarasi 5 emas, 4 ball.
- **Har bir yangi ilova o'rnatilganda oyna chiqadi**: tekshiruv logi (manba, imzolar bazasi, ruxsatlar, APK ichi), topilgan risklar va ularning ballari, xulosa — **virus / shubhali / virus emas** — hamda "Qoldirish" va "O'chirish" tugmalari. Manbadan qat'i nazar hamma ilova tekshiriladi:

| Qayerdan o'rnatilgan | Hukm |
|---|---|
| Play Market, boshqa rasmiy do'kon, Qo'riqchi kaliti | Faqat imzolar bazasi bo'yicha. Ruxsatlar risk sifatida ko'rsatiladi, ball qo'shmaydi |
| USB (`adb`), brauzer (Google/Chrome), fayl menejeri | To'liq ball tizimi. Qaror sizda |
| Telegram, WhatsApp va boshqa messenjerlar | To'liq ball tizimi. Aniq virus log ko'rsatilgach **o'zi o'chiriladi** |

  Yangilanish toza bo'lsa oyna chiqmaydi (Play har kuni o'nlab ilovani yangilaydi) — faqat xavf topilsa. "Qoldirish" bosilgan ilova yangilanishlarda va to'liq skanerda tekshirilmaydi; u o'chirilsa, bu belgi ham olib tashlanadi.
- Xotiraning istalgan joyiga tushgan har bir yangi faylni tekshiradi (Telegram, WhatsApp, Download, Bluetooth va boshqalar).
- Xavfli fayl kelsa **O'chirish** tugmali bildirishnoma chiqadi; aniq virus fayli so'ramasdan o'chiriladi.

**Web himoya** (menyuda alohida yoqiladi, standart holatda o'chiq)
- Brauzerda ochilgan sayt manzili tekshiriladi: Chrome, Samsung Internet, Firefox, Opera, Edge, Brave, Yandex va boshqalar. Telegram'dagi havola Chrome tabida ochilsa, u ham tekshiriladi.
- **Aniq xavfli sayt** (8+ ball) darhol yopiladi (brauzer bir sahifa orqaga qaytadi), keyin sababi ko'rsatiladi. **Shubhali sayt** (5–7 ball) uchun ogohlantirish chiqadi va tanlov sizda qoladi. Ikkalasida ham "Baribir ochish" tugmasi bor.
- Manzil satrini Accessibility xizmati o'qiydi — o'sha avtomatik o'chirish xizmati. Brauzerdan faqat manzil satri o'qiladi.

| Belgi | Ball |
|---|---|
| Xavfli saytlar bazasida bor | +20 |
| Soxta brend: `payme`, `click`, `uzcard`, `humo`, `uzum`, banklar, `mygov`, `olx`, `telegram` ... — lekin rasmiy domen emas | +7 |
| Faqat IP-manzil, o'xshash harflar bilan almashtirilgan manzil (punycode), manzil ichida `@` | +4 |
| Sayt `.apk` yuklatmoqchi | +3 |
| Aldamchi so'z (`bonus`, `karta`, `yutuq`, `login`, `kompensatsiya` ...), `.icu`/`.top`/`.xyz` kabi zona | +2 |
| Bepul hosting (`pages.dev`, `vercel.app`, `ngrok` ...), g'alati uzun manzil | +1 |

**Xavfli saytlar bazasi** kuniga bir marta yuklanadi (gzip bilan ~1,4 MB, mobil internetda 3 kunda bir marta). Hozir ~170 ming domen bor:

- [URLhaus](https://urlhaus.abuse.ch/) (abuse.ch) — virus tarqatayotgan saytlar, [malware-filter](https://gitlab.com/malware-filter/urlhaus-filter) ko'zgusi orqali
- [phishing-filter](https://gitlab.com/malware-filter/phishing-filter) — PhishTank, OpenPhish va PhishStats asosida
- [Phishing Army](https://phishing.army/)

Tekshiruv telefonning o'zida bo'ladi: siz kirgan saytlar hech qayerga yuborilmaydi. Ilova internetga faqat shu ro'yxatlarni yuklab olish uchun chiqadi.

**O'chirish**
- **Avtomatik o'chirish** (Accessibility xizmati) yoqilgan bo'lsa, tizimning "O'chirilsinmi?" oynasidagi OK tugmasini Qo'riqchi o'zi bosadi. Xizmat faqat Qo'riqchi o'zi ochgan o'chirish oynasini, faqat oynada o'sha ilova nomi bo'lsa bosadi. Web himoya yoqilgan bo'lsa, brauzerning manzil satrini ham o'qiydi. Boshqa oynalarni o'qimaydi. Himoya yoqilganda uni yoqish taklif qilinadi.
- Ilova o'chgach, **qoldiqlari** ham tozalanadi: uni o'rnatgan APK nusxalari (Telegram, Download ...), u umumiy xotiraga yozgan APK/kod fayllari (aniq ma'lum virus bo'lsa, yozgan hamma fayllari), `Android/data|media|obb/<paket>` papkalari. Ilovaning ichki ma'lumotlari va keshini tizimning o'zi o'chiradi.
- Fayllar nol bilan qayta yozilib, keyin o'chiriladi. Real-time himoya aniq virus faylini (ma'lum imzo, yolg'on kengaytma, chalkashtirilgan APK) so'ramasdan o'chiradi.
- Skanerdan keyin ro'yxat chiqadi, nimani o'chirishni o'zingiz tanlaysiz.

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

3. **Chuqur tahlil** (do'kondan kelmagan ilovalar va APK fayllar). APK ichi ochib ko'riladi:

| Belgi | Ball |
|---|---|
| ZIP tuzilmasi ataylab buzilgan (Android ochadi, oddiy o'quvchi ochmaydi) | +4 |
| `AndroidManifest.xml/...`, `classes.dex/...`, `////` kabi soxta yozuvlar | +5 |
| Ichida boshqa APK | +4 |
| `assets/` ichida shifrlangan yuk (64 KB+, entropiya > 7.95, ma'lum format emas) | +3 |
| Packer kutubxonasi (jiagu, secneo, DexHelper ...) | +3 |
| Kodda `api.telegram.org` (ma'lumotni Telegram-botga jo'natish) | +4 |
| Yashirin kod yuklash (`DexClassLoader`, ichki `.dex`/`.jar`) | +2 |
| `.icu`, `.top`, `.xyz` ... domenli URL | +2 |

Ichida yashirin yuk bo'lib, boshqa APK o'rnatish huquqini so'rasa — avtomatik `MALWARE` (dropper). SMS o'qib, Telegram-botga jo'natsa — avtomatik `MALWARE` (bank troyani).

Rasmiy do'kondan (Play Market, Galaxy Store, Xiaomi GetApps va boshqalar) va telefon ishlab chiqaruvchisidan kelgan ilovalarga ruxsatlar bo'yicha ball qo'yilmaydi (real-time oynada ular risk sifatida ko'rinadi). Aks holda har bir messenjer "shubhali" bo'lib chiqardi.

## Cheklovlar

Antivirusning nima qila olmasligini bilib qo'ying:

- **O'rnatishni bloklay olmaydi.** Android buni hech bir antivirusga ruxsat bermaydi. Qo'riqchi virusni o'rnatilganidan keyin bir necha soniyada o'chiradi (emulyatorda: 4 soniya).
- **Avtomatik o'chirish uchun Accessibility kerak.** Usiz Android begona ilovani tasdiqsiz o'chirishga ruxsat bermaydi. Android 13+ da Play Market'dan tashqaridan o'rnatilgan ilovada bu tugma kulrang bo'lishi mumkin: **Ilova haqida → ⋮ → Cheklangan sozlamalarga ruxsat**.
- **Qurilma administratori bo'lib olgan virusni** tizim avval admin huquqini olib tashlamaguncha o'chirmaydi — buni qo'lda qilish kerak.
- **Siz ochgan virusni to'xtata olmaydi.** U internetda qilib ulgurgan ishni ham qaytara olmaydi. Ogohlantirish chiqsa — **ochmang**.
- **Telegram'ning yopiq keshini ko'ra olmaydi.** Telegram faylni o'zining yopiq papkasida saqlasa, ogohlantirish faylni o'rnatishni bosganingizda chiqadi. Baribir ochishdan oldin.
- **Evristika 100% emas.** Yangi, hali hech kim ko'rmagan virus o'tib ketishi mumkin.
- **Web himoya faqat brauzerda ishlaydi.** Telegram'ning o'z ichki brauzerida yoki boshqa ilovalar ichida ochilgan sayt tekshirilmaydi: buning uchun o'sha ilova oynasini o'qish kerak bo'lardi, ya'ni xabarlaringizni. Telegram chat sozlamalarida ilova ichidagi brauzerni (In-App Browser) o'chirib qo'ysangiz, havolalar Chrome'da ochiladi va tekshiriladi.
- **Sayt ochilgandan keyin yopiladi.** Qo'riqchi brauzerning tarmog'iga aralashmaydi, faqat manzil satrini ko'radi. Shuning uchun sahifa bir lahza ko'rinishi mumkin — lekin unga karta raqami yoki parol yozishga ulgurmaysiz.

## Xiaomi / Redmi / POCO egalari uchun

MIUI fondagi ilovalarni o'chirib qo'yadi. Himoya doim ishlashi uchun:

1. **Sozlamalar → Ilovalar → Ruxsatlar → Avtozapusk** → Qo'riqchi'ni yoqing.
2. **Ilova sozlamalari → Batareya → Cheklovsiz**.
3. **Ilova sozlamalari → Boshqa ruxsatlar → Fonda ishlayotganda qalqib chiquvchi oynalarni ko'rsatish** → ruxsat. Busiz avtomatik o'chirish fonda ishlamasligi mumkin.

## Tahlil qilingan namuna

`To`ydan arxivi(7).jpeg.apk` — Telegram'da buzilgan akkauntlar orqali tarqatilgan dropper.

| | |
|---|---|
| SHA-256 | `cac75f4531eddb1984650d9a88c91bd79560331bfed0de30afee62dde432840f` |
| Paket | `net.champion.golden` |
| Server | `asdzxcqwe.icu` (ochilganda `/webview/photo?buildVersion=asdsgsva01` sahifasini yuklaydi) |
| Xatti-harakati | O'zini to'y rasmlari arxivi qilib ko'rsatadi, 2-bosqich virusni o'rnatish huquqini so'raydi. Ichida shifrlangan yuk bor, VPN xizmati va yashirin "Play" ikonkasi bor, Xiaomi/Huawei/Vivo xavfsizlik ilovalarini qidiradi. |

Tahlil internetsiz Android emulyatorida o'tkazilgan.

## iPhone versiyasi

iOS boshqa ilovalarni ko'rish, skanerlash yoki o'chirishga hech bir ilovaga ruxsat bermaydi, iPhone APK ham o'rnatmaydi. Shuning uchun iPhone versiyasi antivirus emas — **firibgar saytlardan himoya va xavfsizlik tekshiruvi**:

- **Safari bloklovchi** — Android'dagi xavfli saytlar bazasi (~140 ming domen; Safari bitta kengaytmada ~150 mingtagacha qoida oladi) va soxta Payme/Click/bank/Apple ID saytlari uchun qoidalar. Bunday sayt Safari'da umuman ochilmaydi.
- **SMS filtri** — notanish raqamdan kelgan, firibgar havolali SMS "Junk" bo'limiga tushadi.
- **Havolani tekshirish** — shubhali havolani nusxalab qo'yasiz, sababi bilan javob chiqadi.
- **Xavfsizlik tekshiruvi** — iOS versiyasi, ekran paroli, jailbreak belgilari.

Telegram'ning ichki brauzerini iOS tekshirishga ruxsat bermaydi: Telegram'da havolalarni Safari'da ochadigan qilib sozlang. Barcha ilovalardagi saytlarni bloklaydigan DNS-filtr uchun Apple pullik dasturchi akkaunti kerak — u hozircha yo'q.

**O'rnatish (Mac'siz, Windows'dan):**

1. [Actions](../../actions/workflows/ios.yml) → oxirgi muvaffaqiyatli yig'ish → `Qoriqchi-ipa` faylini yuklab oling.
2. Kompyuterga [iTunes](https://www.apple.com/itunes/) (Microsoft Store'dagisi emas) va [Sideloadly](https://sideloadly.io/) ni o'rnating.
3. iPhone'ni kabel bilan ulang, Sideloadly'ga IPA'ni tashlang va Apple ID'ingizni kiriting.
4. iPhone'da: **Sozlamalar → Maxfiylik va xavfsizlik → Dasturchi rejimi** → yoqing (telefon qayta yonadi). So'ng **Sozlamalar → Umumiy → VPN va qurilmani boshqarish** → Apple ID'ingizga ishoning.

Bepul Apple ID bilan ilova 7 kun ishlaydi, keyin Sideloadly orqali qayta o'rnatiladi.

Kod: `ios/` — umumiy mantiq `ios/Core` (Swift paket, testlari `swift test` bilan), ilova SwiftUI'da, Xcode loyihasi `ios/project.yml`dan [XcodeGen](https://github.com/yonaskolb/XcodeGen) bilan yaratiladi.

## O'zingiz yig'ish

Kerak bo'ladi: JDK 17 va Android SDK (compileSdk 36).

```bash
./gradlew assembleHackerDebug      # hacker versiyasi
./gradlew assembleFamilyDebug      # oila versiyasi
./gradlew dist                     # ikkala release APK -> dist/
./gradlew testHackerDebugUnitTest  # sayt qoidalari va baza testlari (bittasi haqiqiy ro'yxatlarni yuklaydi, internet kerak)
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
app/src/main/     umumiy dvigatel: Rules (qoidalar, imzolar), Scanner, Deep (APK ichini tahlil),
                  GuardService (real-time), Remover (o'chirish + qoldiqlar), AutoRemove (avto-tasdiq),
                  WebGuard (sayt qoidalari, brauzer manzil satri), WebDb (xavfli saytlar bazasi),
                  IntroView + Motion (kirish animatsiyasi, menyu animatsiyalari)
app/src/hacker/   hacker interfeysi: terminal ekranlari, matnlar
app/src/family/   oila interfeysi: kartochkalar, oddiy tushuntirishlar (Explain)
app/src/test/     unit testlar (JUnit)
```

Tashqi kutubxonalar yo'q — faqat Android SDK va Kotlin (testlar uchun JUnit).
