import SafariServices
import SwiftUI

/// Bosh ekran: himoya holati va uni yoqish bo'yicha qadamlar.
struct ProtectionView: View {
    @EnvironmentObject private var updater: Updater
    @Environment(\.scenePhase) private var phase
    @State private var safariOn: Bool?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    status.staggerIn(0)

                    Card(icon: "safari", tint: safariOn == true ? Theme.ok : Theme.warn, title: Voice.safari(safariOn == true)) {
                        if safariOn == true {
                            Text("Firibgar va virusli saytlar Safari'da ochilmaydi.")
                        } else {
                            Text("Sozlamalar → Ilovalar → Safari → Kengaytmalar → \(appName) → yoqing.\n(Настройки → Приложения → Safari → Расширения)")
                        }
                    }
                    .staggerIn(1)

                    Card(icon: "message.badge", tint: Theme.accent, title: Voice.sms) {
                        Text("Sozlamalar → Ilovalar → Xabarlar → Notanish va spam → SMS filtrlash → \(appName).\n(Настройки → Приложения → Сообщения → Неизвестные и спам)\n\nNotanish raqamdan kelgan firibgar havolali SMS'lar \"Junk\" bo'limiga tushadi.")
                    }
                    .staggerIn(2)

                    Card(icon: "paperplane", tint: Theme.accent, title: Voice.telegram) {
                        Text("Telegram sozlamalarida havolalar ochiladigan brauzerni (Browser / Open links in) Safari qilib qo'ying. Shunda Telegram'dagi havolalar ham Safari himoyasidan o'tadi — Telegram'ning ichki brauzerini iOS tekshirishga ruxsat bermaydi.")
                    }
                    .staggerIn(3)

                    database.staggerIn(4)
                }
                .padding()
            }
            .background(Theme.background)
            .navigationTitle(Voice.protectionTitle)
            .task { await refreshSafari() }
            .onChange(of: phase) { p in
                if p == .active { Task { await refreshSafari() } }
            }
        }
    }

    /// Sozlamalarda ko'rinadigan nom (versiyaga qarab "Qo'riqchi" yoki "QO'RIQCHI").
    private var appName: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String ?? "Qo'riqchi"
    }

    private var status: some View {
        let on = safariOn == true
        return VStack(spacing: 12) {
            Image(systemName: on ? "checkmark.shield.fill" : "exclamationmark.shield.fill")
                .font(.system(size: 64))
                .foregroundStyle(on ? Theme.ok : Theme.warn)
                .symbolRenderingMode(.hierarchical)
                .shadow(color: Flavor.hacker ? Theme.neon.opacity(on ? 0.6 : 0) : .clear, radius: 14)
                .breathing(on)
            Text(Voice.status(on))
                .font(.title2.bold())
                .foregroundStyle(Flavor.hacker ? (on ? Theme.ok : Theme.warn) : Color.primary)
            Text(Voice.statusDetail(on, domains: updater.domains))
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .cardBackground()
    }

    private var database: some View {
        Card(icon: "externaldrive.badge.checkmark", tint: Theme.accent, title: Voice.database) {
            VStack(alignment: .leading, spacing: 8) {
                if let updated = updater.updated {
                    Text("\(updater.domains.formatted()) ta domen · \(updated.formatted(.relative(presentation: .named)))")
                } else {
                    Text("Hali yuklanmagan. Hozircha ichki qoidalar ishlaydi (soxta Payme, Click, bank saytlari).")
                }
                if updater.failed {
                    Text("Yangilab bo'lmadi — internetni tekshiring.").foregroundStyle(Theme.warn)
                }
                Text("URLhaus + phishing ro'yxatlari. Havola tekshiruvi bazani kuniga bir marta o'zi yangilaydi; Safari va SMS filtridagi baza ilovani qayta o'rnatganingizda (7 kunda bir) yangilanadi. Siz kirgan saytlar hech qayerga yuborilmaydi.")
                    .font(.footnote)
                Button {
                    Task { await updater.update() }
                } label: {
                    if updater.running {
                        ProgressView().frame(maxWidth: .infinity)
                    } else {
                        Text(Voice.update).frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.bordered)
                .disabled(updater.running)
                .padding(.top, 4)
            }
        }
    }

    private func refreshSafari() async {
        let state = try? await SFContentBlockerManager.stateOfContentBlocker(withIdentifier: BlocklistSync.blockerID)
        withAnimation(.spring(response: 0.4)) { safariOn = state?.isEnabled ?? false }
    }
}

/// Himoya yoqilganda qalqon sekin "nafas oladi".
private struct Breathing: ViewModifier {
    let active: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var big = false

    func body(content: Content) -> some View {
        content
            .scaleEffect(big ? 1.06 : 1)
            .onAppear { update() }
            .onChange(of: active) { _ in update() }
    }

    private func update() {
        guard active, !reduceMotion else {
            withAnimation(.default) { big = false }
            return
        }
        withAnimation(.easeInOut(duration: 1.5).repeatForever(autoreverses: true)) { big = true }
    }
}

private extension View {
    func breathing(_ active: Bool) -> some View { modifier(Breathing(active: active)) }
}
