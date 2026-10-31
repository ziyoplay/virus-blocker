import SwiftUI
import UIKit

/// Ikki versiya, bitta kod — Android'dagi kabi:
/// - hacker (`HACKER` belgisi bilan yig'iladi): qora terminal, neon-yashil, monospace
/// - oila: oq/ko'k, sokin, oddiy til
enum Flavor {
    #if HACKER
    static let hacker = true
    #else
    static let hacker = false
    #endif
}

enum Theme {
    static let neon = Color(red: 0, green: 1, blue: 0.255)
    static let neonDim = Color(red: 0.04, green: 0.56, blue: 0.18)
    static let termBg = Color(red: 0.008, green: 0.04, blue: 0.016)

    static let accent = Flavor.hacker ? neon : Color.blue
    static let ok = Flavor.hacker ? neon : Color.green
    static let warn = Flavor.hacker ? Color(red: 1, green: 0.77, blue: 0) : Color.orange
    static let danger = Flavor.hacker ? Color(red: 1, green: 0.17, blue: 0.17) : Color.red
    static let background = Flavor.hacker ? Color.black : Color(.systemGroupedBackground)
    static let card = Flavor.hacker ? termBg : Color(.secondarySystemGroupedBackground)
    static let field = Flavor.hacker ? Color.black : Color(.tertiarySystemGroupedBackground)
    static let radius: CGFloat = Flavor.hacker ? 6 : 18

    /// Navigatsiya va tab paneli: hacker'da qora fon, yashil monospace sarlavhalar.
    static func configureAppearance() {
        guard Flavor.hacker else { return }
        let green = UIColor(red: 0, green: 1, blue: 0.255, alpha: 1)
        let nav = UINavigationBarAppearance()
        nav.configureWithOpaqueBackground()
        nav.backgroundColor = .black
        nav.shadowColor = UIColor(red: 0.04, green: 0.56, blue: 0.18, alpha: 0.6)
        nav.titleTextAttributes = [.foregroundColor: green, .font: UIFont.monospacedSystemFont(ofSize: 17, weight: .bold)]
        nav.largeTitleTextAttributes = [.foregroundColor: green, .font: UIFont.monospacedSystemFont(ofSize: 30, weight: .bold)]
        UINavigationBar.appearance().standardAppearance = nav
        UINavigationBar.appearance().scrollEdgeAppearance = nav
        UINavigationBar.appearance().compactAppearance = nav

        let tab = UITabBarAppearance()
        tab.configureWithOpaqueBackground()
        tab.backgroundColor = .black
        tab.shadowColor = UIColor(red: 0.04, green: 0.56, blue: 0.18, alpha: 0.6)
        let item = tab.stackedLayoutAppearance
        item.normal.iconColor = UIColor(red: 0.04, green: 0.56, blue: 0.18, alpha: 1)
        item.normal.titleTextAttributes = [.foregroundColor: UIColor(red: 0.04, green: 0.56, blue: 0.18, alpha: 1),
                                           .font: UIFont.monospacedSystemFont(ofSize: 10, weight: .regular)]
        item.selected.iconColor = green
        item.selected.titleTextAttributes = [.foregroundColor: green, .font: UIFont.monospacedSystemFont(ofSize: 10, weight: .bold)]
        UITabBar.appearance().standardAppearance = tab
        UITabBar.appearance().scrollEdgeAppearance = tab
    }
}

extension View {
    /// Butun ilovaga versiya uslubini beradi.
    @ViewBuilder
    func flavorStyle() -> some View {
        if Flavor.hacker {
            self.fontDesign(.monospaced)
                .foregroundStyle(Theme.neon)
                .tint(Theme.neon)
                .preferredColorScheme(.dark)
        } else {
            self.tint(.blue)
        }
    }
}

/// Sarlavhalar: oila — oddiy til, hacker — terminal uslubi. Tushuntirish matnlari ikkalasida bir xil.
enum Voice {
    static let tabProtection = Flavor.hacker ? "guard" : "Himoya"
    static let tabLink = Flavor.hacker ? "scan-url" : "Havola"
    static let tabAudit = Flavor.hacker ? "audit" : "Tekshiruv"

    static let protectionTitle = Flavor.hacker ? "> himoya" : "Himoya"
    static let linkTitle = Flavor.hacker ? "> scan --url" : "Havolani tekshirish"
    static let auditTitle = Flavor.hacker ? "> audit" : "Xavfsizlik"

    static func status(_ on: Bool) -> String {
        Flavor.hacker ? (on ? "[✓] HIMOYA FAOL" : "[!] HIMOYA TO'LIQ EMAS") : (on ? "iPhone himoyada" : "Himoya hali to'liq yoqilmagan")
    }

    static func statusDetail(_ on: Bool, domains: Int) -> String {
        if Flavor.hacker {
            return on ? "web-baza: \(domains.formatted()) domen\nsafari.blocker + sms.filter: ON" : "quyidagi modullarni yoqing"
        }
        return on ? "Bazada \(domains.formatted()) ta xavfli sayt" : "Pastdagi qadamlarni bajaring — 1 daqiqa vaqt oladi."
    }

    static func safari(_ on: Bool) -> String {
        Flavor.hacker ? "[1] safari.blocker: " + (on ? "[ON]" : "[OFF]") : (on ? "Safari himoyasi yoqilgan" : "1. Safari himoyasini yoqing")
    }

    static let sms = Flavor.hacker ? "[2] sms.filter" : "2. SMS filtrini yoqing"
    static let telegram = Flavor.hacker ? "[3] telegram --open-in safari" : "3. Telegram havolalari"
    static let database = Flavor.hacker ? "[*] web-baza" : "Xavfli saytlar bazasi"
    static let update = Flavor.hacker ? "> update --now" : "Hozir yangilash"

    static let pasteLink = Flavor.hacker ? "[>] url kiriting" : "Havolani qo'ying"
    static let check = Flavor.hacker ? "SCAN" : "Tekshirish"
    static let remember = Flavor.hacker ? "[i] eslatma" : "Esda tuting"
    static let invalid = Flavor.hacker ? "[?] url emas" : "Bu havolaga o'xshamaydi"
    static let clean = Flavor.hacker ? "[✓] TOZA" : "Xavf topilmadi"

    static func danger(_ malware: Bool) -> String {
        Flavor.hacker ? (malware ? "☠ PHISHING / MALWARE" : "⚠ SHUBHALI") : (malware ? "Xavfli sayt!" : "Shubhali sayt")
    }

    static let auditFooter = Flavor.hacker ? "pull-to-refresh → qayta skan" : "Pastga torting — qayta tekshiriladi."
    static let manualChecks = Flavor.hacker ? "manual --check" : "O'zingiz tekshiring"
}
