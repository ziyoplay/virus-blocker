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

                    Card(icon: "safari", tint: safariOn == true ? .green : .orange, title: safariOn == true ? "Safari himoyasi yoqilgan" : "1. Safari himoyasini yoqing") {
                        if safariOn == true {
                            Text("Firibgar va virusli saytlar Safari'da ochilmaydi.")
                        } else {
                            Text("Sozlamalar → Ilovalar → Safari → Kengaytmalar → Qo'riqchi → yoqing.\n(Settings → Apps → Safari → Extensions)")
                        }
                    }
                    .staggerIn(1)

                    Card(icon: "message.badge", tint: .blue, title: "2. SMS filtrini yoqing") {
                        Text("Sozlamalar → Ilovalar → Xabarlar → Notanish va spam → SMS filtrlash → Qo'riqchi.\n(Settings → Apps → Messages → Unknown & Spam)\n\nNotanish raqamdan kelgan firibgar havolali SMS'lar \"Junk\" bo'limiga tushadi.")
                    }
                    .staggerIn(2)

                    Card(icon: "paperplane", tint: .blue, title: "3. Telegram havolalari") {
                        Text("Telegram sozlamalarida havolalar ochiladigan brauzerni (Browser / Open links in) Safari qilib qo'ying. Shunda Telegram'dagi havolalar ham Safari himoyasidan o'tadi — Telegram'ning ichki brauzerini iOS tekshirishga ruxsat bermaydi.")
                    }
                    .staggerIn(3)

                    database.staggerIn(4)
                }
                .padding()
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Himoya")
            .task { await refreshSafari() }
            .onChange(of: phase) { p in
                if p == .active { Task { await refreshSafari() } }
            }
        }
    }

    private var status: some View {
        let on = safariOn == true
        return VStack(spacing: 12) {
            Image(systemName: on ? "checkmark.shield.fill" : "exclamationmark.shield.fill")
                .font(.system(size: 64))
                .foregroundStyle(on ? Color.green : Color.orange)
                .symbolRenderingMode(.hierarchical)
                .breathing(on)
            Text(on ? "iPhone himoyada" : "Himoya hali to'liq yoqilmagan")
                .font(.title2.bold())
            Text(on ? "Bazada \(updater.domains.formatted()) ta xavfli sayt" : "Pastdagi qadamlarni bajaring — 1 daqiqa vaqt oladi.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var database: some View {
        Card(icon: "externaldrive.badge.checkmark", tint: .blue, title: "Xavfli saytlar bazasi") {
            VStack(alignment: .leading, spacing: 8) {
                if let updated = updater.updated {
                    Text("\(updater.domains.formatted()) ta domen · \(updated.formatted(.relative(presentation: .named)))")
                } else {
                    Text("Hali yuklanmagan. Hozircha ichki qoidalar ishlaydi (soxta Payme, Click, bank saytlari).")
                }
                if updater.failed {
                    Text("Yangilab bo'lmadi — internetni tekshiring.").foregroundStyle(.orange)
                }
                Text("URLhaus + phishing ro'yxatlari, kuniga bir marta yangilanadi. Siz kirgan saytlar hech qayerga yuborilmaydi.")
                    .font(.footnote)
                Button {
                    Task { await updater.update() }
                } label: {
                    if updater.running {
                        ProgressView().frame(maxWidth: .infinity)
                    } else {
                        Text("Hozir yangilash").frame(maxWidth: .infinity)
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
