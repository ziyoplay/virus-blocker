import LocalAuthentication
import SafariServices
import SwiftUI

/// iPhone xavfsizlik tekshiruvi: iOS versiyasi, ekran paroli, jailbreak belgilari, Safari himoyasi.
enum SecurityCheck {
    struct Item: Identifiable {
        let id: String
        let title: String
        let detail: String
        let ok: Bool
    }

    /// Xavfsizlik yangilanishlarini oladigan eng eski asosiy versiya. Yangi iOS chiqqanda oshiriladi.
    static let minSupportedMajor = 26

    static func run() async -> [Item] {
        var out: [Item] = []

        let v = ProcessInfo.processInfo.operatingSystemVersion
        let version = "\(v.majorVersion).\(v.minorVersion)" + (v.patchVersion > 0 ? ".\(v.patchVersion)" : "")
        let fresh = v.majorVersion >= minSupportedMajor
        out.append(Item(
            id: "ios",
            title: "iOS \(version)",
            detail: fresh
                ? "Tizim yangi. Baribir Sozlamalar → Umumiy → Dasturiy ta'minotni yangilash'da yangilanish bor-yo'qligini tekshirib turing."
                : "Eskirgan. Sozlamalar → Umumiy → Dasturiy ta'minotni yangilash. Eski iOS'dagi teshiklardan firibgarlar foydalanadi.",
            ok: fresh
        ))

        let passcode = LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil)
        out.append(Item(
            id: "passcode",
            title: passcode ? "Ekran paroli o'rnatilgan" : "Ekran paroli yo'q",
            detail: passcode
                ? "Telefon yo'qolsa, undagi ma'lumotlar himoyalangan."
                : "Sozlamalar → Face ID va kod-parol. Parolsiz telefon qo'lga tushsa, hamma narsa ochiq.",
            ok: passcode
        ))

        let jailbroken = isJailbroken()
        out.append(Item(
            id: "jailbreak",
            title: jailbroken ? "Jailbreak belgilari topildi" : "Jailbreak yo'q",
            detail: jailbroken
                ? "iOS himoyasi o'chirilgan: har qanday ilova telefonni to'liq boshqarishi mumkin. Agar buni o'zingiz qilmagan bo'lsangiz — telefonni tiklang."
                : "iOS'ning o'z himoyasi joyida.",
            ok: !jailbroken
        ))

        let safari = (try? await SFContentBlockerManager.stateOfContentBlocker(withIdentifier: BlocklistSync.blockerID))?.isEnabled ?? false
        out.append(Item(
            id: "safari",
            title: safari ? "Safari himoyasi yoqilgan" : "Safari himoyasi o'chiq",
            detail: safari
                ? "Firibgar saytlar Safari'da ochilmaydi."
                : "Sozlamalar → Ilovalar → Safari → Kengaytmalar → Qo'riqchi.",
            ok: safari
        ))
        return out
    }

    static func isJailbroken() -> Bool {
        #if targetEnvironment(simulator)
        return false
        #else
        let paths = [
            "/Applications/Cydia.app", "/Applications/Sileo.app", "/var/jb", "/Library/MobileSubstrate/MobileSubstrate.dylib",
            "/bin/bash", "/usr/sbin/sshd", "/etc/apt", "/private/var/lib/apt/",
        ]
        if paths.contains(where: { FileManager.default.fileExists(atPath: $0) }) { return true }
        // Oddiy iPhone'da ilova o'z papkasidan tashqariga yoza olmaydi.
        let probe = "/private/qoriqchi-jb-\(UUID().uuidString).txt"
        if (try? "x".write(toFile: probe, atomically: true, encoding: .utf8)) != nil {
            try? FileManager.default.removeItem(atPath: probe)
            return true
        }
        return false
        #endif
    }
}

struct SecurityView: View {
    @State private var items: [SecurityCheck.Item] = []

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(Array(items.enumerated()), id: \.element.id) { i, item in
                        HStack(alignment: .top, spacing: 14) {
                            Image(systemName: item.ok ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                                .font(.title2)
                                .foregroundStyle(item.ok ? Color.green : Color.orange)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.title).font(.headline)
                                Text(item.detail).font(.subheadline).foregroundStyle(.secondary)
                            }
                        }
                        .padding(.vertical, 4)
                        .staggerIn(i)
                    }
                } footer: {
                    Text("Pastga torting — qayta tekshiriladi.")
                }

                Section("O'zingiz tekshiring") {
                    tip("Profillar", "Sozlamalar → Umumiy → VPN va qurilmani boshqarish. Siz o'rnatmagan profil bo'lsa — o'chiring: firibgarlar u orqali trafikni kuzatadi.")
                    tip("Apple ID", "Ikki bosqichli tasdiqlash yoqilgan bo'lsin. Apple hech qachon SMS'da havola yuborib parol so'ramaydi.")
                    tip("Ilovalar", "Ilovani faqat App Store'dan o'rnating. \"Ishonchli dasturchi\" deb profil o'rnatishni so'rashsa — bu firibgarlik.")
                }
            }
            .navigationTitle("Xavfsizlik")
            .task { items = await SecurityCheck.run() }
            .refreshable { items = await SecurityCheck.run() }
        }
    }

    private func tip(_ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.headline)
            Text(text).font(.subheadline).foregroundStyle(.secondary)
        }
        .padding(.vertical, 2)
    }
}
