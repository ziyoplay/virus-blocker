import QoriqchiCore
import SwiftUI

/// Havolani qo'lda tekshirish: Telegram yoki SMS'dagi shubhali havolani nusxalab, shu yerga qo'yasiz.
struct LinkCheckView: View {
    private enum Outcome: Hashable {
        case clean(String)
        case danger(host: String, level: ThreatLevel, score: Int, reasons: [String])
        case invalid
    }

    @State private var text = ""
    @State private var outcome: Outcome?
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    Card(icon: "link", tint: .blue, title: "Havolani qo'ying") {
                        VStack(spacing: 12) {
                            TextField("payme-bonus.top/login", text: $text)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .keyboardType(.URL)
                                .submitLabel(.go)
                                .focused($focused)
                                .onSubmit(check)
                                .padding(12)
                                .background(Color(.tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                                .foregroundStyle(.primary)
                            HStack {
                                PasteButton(payloadType: String.self) { strings in
                                    Task { @MainActor in
                                        text = strings.first ?? ""
                                        check()
                                    }
                                }
                                .labelStyle(.titleAndIcon)
                                Spacer()
                                Button("Tekshirish", action: check)
                                    .buttonStyle(.borderedProminent)
                                    .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty)
                            }
                        }
                    }
                    .staggerIn(0)

                    if let outcome {
                        result(outcome)
                            .transition(.asymmetric(insertion: .scale(scale: 0.9).combined(with: .opacity), removal: .opacity))
                            .id(outcome)
                    }

                    Card(icon: "lightbulb", tint: .yellow, title: "Esda tuting") {
                        Text("Bank, Payme yoki Click hech qachon SMS yoki Telegram orqali havola yuborib, karta raqami va SMS-kodni so'ramaydi. Bunday xabar kelsa — havolani ochmang.")
                    }
                    .staggerIn(1)
                }
                .padding()
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Havolani tekshirish")
            .scrollDismissesKeyboard(.interactively)
        }
    }

    @ViewBuilder
    private func result(_ outcome: Outcome) -> some View {
        switch outcome {
        case .invalid:
            Card(icon: "questionmark.circle", tint: .secondary, title: "Bu havolaga o'xshamaydi") {
                Text("Sayt manzilini to'liq qo'ying, masalan: example.com/sahifa")
            }
        case let .clean(host):
            Card(icon: "checkmark.shield.fill", tint: .green, title: "Xavf topilmadi") {
                Text("\"\(host)\" bazada yo'q va unda firibgarlik belgilari topilmadi. Baribir karta ma'lumotlarini faqat rasmiy ilovalarda kiriting.")
            }
        case let .danger(host, level, score, reasons):
            Card(icon: level == .malware ? "xmark.shield.fill" : "exclamationmark.shield.fill",
                 tint: level == .malware ? .red : .orange,
                 title: level == .malware ? "Xavfli sayt!" : "Shubhali sayt") {
                VStack(alignment: .leading, spacing: 6) {
                    Text("\"\(host)\" · xavf \(score)").font(.subheadline.bold()).foregroundStyle(.primary)
                    ForEach(reasons, id: \.self) { Text("• " + $0) }
                    Text("Bu saytga karta raqami, SMS-kod yoki parolingizni kiritmang.")
                        .padding(.top, 4)
                        .foregroundStyle(.primary)
                }
            }
        }
    }

    private func check() {
        focused = false
        let raw = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let host = WebRules.host(of: raw) else {
            withAnimation(.spring(response: 0.4)) { outcome = .invalid }
            return
        }
        let sets = SharedStore.loadSets()
        let verdict = WebRules.check(raw) { Blocklist.lookup(sets, host: $0) }
        withAnimation(.spring(response: 0.45, dampingFraction: 0.75)) {
            if let v = verdict {
                outcome = .danger(host: v.host, level: v.level, score: v.score, reasons: v.reasons)
            } else {
                outcome = .clean(host)
            }
        }
    }
}
