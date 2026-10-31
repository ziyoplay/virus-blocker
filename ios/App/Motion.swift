import SwiftUI

/// 24x24 koordinatalardagi qalqon (Android'dagi ikonka bilan bir xil shakl).
struct ShieldShape: Shape {
    func path(in rect: CGRect) -> Path {
        let s = min(rect.width, rect.height) / 24
        let ox = rect.midX - 12 * s
        let oy = rect.midY - 12 * s
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: ox + x * s, y: oy + y * s) }
        var path = Path()
        path.move(to: p(12, 1))
        path.addLine(to: p(3, 5))
        path.addLine(to: p(3, 11))
        path.addCurve(to: p(12, 23), control1: p(3, 16.55), control2: p(6.84, 21.74))
        path.addCurve(to: p(21, 11), control1: p(17.16, 21.74), control2: p(21, 16.55))
        path.addLine(to: p(21, 5))
        path.closeSubpath()
        return path
    }
}

struct CheckShape: Shape {
    func path(in rect: CGRect) -> Path {
        let s = min(rect.width, rect.height) / 24
        let ox = rect.midX - 12 * s
        let oy = rect.midY - 12 * s
        var path = Path()
        path.move(to: CGPoint(x: ox + 7.5 * s, y: oy + 12.2 * s))
        path.addLine(to: CGPoint(x: ox + 10.5 * s, y: oy + 15.2 * s))
        path.addLine(to: CGPoint(x: ox + 16.5 * s, y: oy + 9.2 * s))
        return path
    }
}

/// Kirish animatsiyasi: qalqon chiziladi → to'ladi → belgi chiziladi → to'lqinlar tarqaladi → nom chiqadi.
/// Bosilsa — kutmasdan o'tib ketadi. "Harakatni kamaytirish" yoqilgan bo'lsa — o'ynalmaydi.
struct IntroView: View {
    var onDone: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var trace: CGFloat = 0
    @State private var filled = false
    @State private var check: CGFloat = 0
    @State private var ripple = false
    @State private var title = false

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            VStack(spacing: 36) {
                ZStack {
                    ForEach(0 ..< 2, id: \.self) { i in
                        Circle()
                            .stroke(Color.accentColor.opacity(ripple ? 0 : 0.35), lineWidth: 2)
                            .scaleEffect(ripple ? 2.6 : 0.6)
                            .animation(.easeOut(duration: 1.1).delay(0.75 + Double(i) * 0.25), value: ripple)
                    }
                    ShieldShape()
                        .trim(from: 0, to: trace)
                        .stroke(Color.accentColor, style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                    ShieldShape()
                        .fill(Color.accentColor)
                        .opacity(filled ? 1 : 0)
                        .scaleEffect(filled ? 1 : 0.88)
                    CheckShape()
                        .trim(from: 0, to: check)
                        .stroke(Color.white, style: StrokeStyle(lineWidth: 9, lineCap: .round, lineJoin: .round))
                }
                .frame(width: 120, height: 120)

                VStack(spacing: 8) {
                    Text("Qo'riqchi").font(.largeTitle.bold())
                    Text("iPhone'ingizni firibgar saytlardan himoya qiladi")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .opacity(title ? 1 : 0)
                .offset(y: title ? 0 : 16)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { onDone() }
        .accessibilityElement(children: .combine)
        .task {
            if reduceMotion {
                onDone()
                return
            }
            withAnimation(.easeInOut(duration: 0.6)) { trace = 1 }
            withAnimation(.spring(response: 0.45, dampingFraction: 0.6).delay(0.45)) { filled = true }
            withAnimation(.easeOut(duration: 0.4).delay(0.85)) { check = 1 }
            ripple = true
            withAnimation(.easeOut(duration: 0.5).delay(1.0)) { title = true }
            try? await Task.sleep(nanoseconds: 1_900_000_000)
            onDone()
        }
    }
}

/// Kartalar ekranga birin-ketin suzib chiqadi.
struct StaggerIn: ViewModifier {
    let index: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown = false

    func body(content: Content) -> some View {
        content
            .opacity(shown || reduceMotion ? 1 : 0)
            .offset(y: shown || reduceMotion ? 0 : 24)
            .onAppear {
                withAnimation(.spring(response: 0.5, dampingFraction: 0.85).delay(0.15 + Double(index) * 0.06)) { shown = true }
            }
    }
}

extension View {
    func staggerIn(_ index: Int) -> some View { modifier(StaggerIn(index: index)) }
}

struct Card<Content: View>: View {
    let icon: String
    let tint: Color
    let title: String
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label {
                Text(title).font(.headline)
            } icon: {
                Image(systemName: icon).foregroundStyle(tint)
            }
            content
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .cardBackground()
    }
}

extension View {
    /// Karta foni: oila — yumaloq oq karta, hacker — qora terminal oynasi, yashil ramka.
    func cardBackground() -> some View {
        background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.radius, style: .continuous))
            .overlay {
                if Flavor.hacker {
                    RoundedRectangle(cornerRadius: Theme.radius, style: .continuous).stroke(Theme.neonDim, lineWidth: 1)
                }
            }
    }
}

/// Hacker versiyasining kirish animatsiyasi (Android'dagi bilan bir xil vaqt chizig'i): CRT chiziqlari,
/// neon qalqon chiziladi, skaner chizig'i uni to'ldiradi, belgi, so'ng sarlavha tasodifiy belgilardan "ochiladi".
struct TerminalIntroView: View {
    var onDone: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var start = Date()
    private let duration = 2.3
    private static let glyphs = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#$%&@<>/")
    private let title = "QO'RIQCHI"
    private let subtitle = "> himoya moduli yuklandi"

    var body: some View {
        TimelineView(.animation) { timeline in
            let t = min(1, timeline.date.timeIntervalSince(start) / duration)
            Canvas { g, size in draw(&g, size, t) }
        }
        .background(Color.black)
        .ignoresSafeArea()
        .contentShape(Rectangle())
        .onTapGesture { onDone() }
        .accessibilityLabel(title)
        .task {
            if reduceMotion {
                onDone()
                return
            }
            start = Date()
            try? await Task.sleep(nanoseconds: UInt64(duration * 1_000_000_000))
            onDone()
        }
    }

    /// Umumiy vaqt chizig'ining [a, b] oralig'idagi ulushi (0..1).
    private func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t - a) / (b - a))) }
    private func ease(_ p: Double) -> Double { 1 - pow(1 - p, 3) }

    private func draw(_ g: inout GraphicsContext, _ size: CGSize, _ t: Double) {
        let green = Theme.neon
        // CRT chiziqlari
        var y: CGFloat = 0
        var lines = Path()
        while y < size.height {
            lines.move(to: CGPoint(x: 0, y: y))
            lines.addLine(to: CGPoint(x: size.width, y: y))
            y += 4
        }
        g.stroke(lines, with: .color(green.opacity(0.055)), lineWidth: 1)

        let side = min(size.width, size.height) * 0.30
        let rect = CGRect(x: size.width / 2 - side / 2, y: size.height * 0.40 - side / 2, width: side, height: side)
        let unit = side / 24
        let shield = ShieldShape().path(in: rect)

        // Neon kontur: bir nechta qalin-shaffof qatlam = yorug'lik
        let trace = shield.trimmedPath(from: 0, to: seg(t, 0, 0.30))
        let layers: [(CGFloat, Double)] = [(2.6, 0.14), (1.4, 0.31), (0.6, 1.0)]
        for (w, a) in layers {
            g.stroke(trace, with: .color(green.opacity(a)), style: StrokeStyle(lineWidth: w * unit, lineCap: .round, lineJoin: .round))
        }

        // Skaner chizig'i qalqonni yuqoridan pastga to'ldiradi
        let sweep = ease(seg(t, 0.28, 0.60))
        if sweep > 0 {
            let scanY = rect.minY + unit * (1 + 22 * CGFloat(sweep))
            var clipped = g
            clipped.clip(to: Path(CGRect(x: rect.minX, y: rect.minY, width: rect.width, height: scanY - rect.minY)))
            clipped.fill(shield, with: .color(green.opacity(0.18)))
            if sweep < 1 {
                var scan = Path()
                scan.move(to: CGPoint(x: rect.minX - 4 * unit, y: scanY))
                scan.addLine(to: CGPoint(x: rect.maxX + 4 * unit, y: scanY))
                g.stroke(scan, with: .color(green.opacity(0.2)), lineWidth: 1.6 * unit)
                g.stroke(scan, with: .color(green), lineWidth: 0.35 * unit)
            }
        }

        let check = CheckShape().path(in: rect).trimmedPath(from: 0, to: ease(seg(t, 0.55, 0.70)))
        g.stroke(check, with: .color(green.opacity(0.2)), style: StrokeStyle(lineWidth: 3.2 * unit, lineCap: .round, lineJoin: .round))
        g.stroke(check, with: .color(Color(red: 0.78, green: 1, blue: 0.83)), style: StrokeStyle(lineWidth: 1.5 * unit, lineCap: .round, lineJoin: .round))

        // Sarlavha chapdan o'ngga "dekodlanadi"
        let solved = Int(seg(t, 0.50, 0.82) * Double(title.count))
        let shown = String(title.enumerated().map { i, c in i < solved || c == " " ? c : Self.glyphs.randomElement()! })
        let titleY = rect.maxY + 64
        var text = g
        text.opacity = ease(seg(t, 0.48, 0.56))
        text.addFilter(.shadow(color: green, radius: 10))
        text.draw(
            Text(shown).font(.system(size: 30, weight: .bold, design: .monospaced)).kerning(7).foregroundColor(green),
            at: CGPoint(x: size.width / 2, y: titleY)
        )

        let typed = seg(t, 0.74, 0.95)
        if typed > 0 {
            let cursor = Int(t * 12) % 2 == 0 ? "█" : " "
            let line = String(subtitle.prefix(Int(typed * Double(subtitle.count)))) + cursor
            // Monospace: to'liq uzunlikka to'ldirsak, yozilayotganda markazdan siljimaydi
            let padded = line.padding(toLength: subtitle.count + 1, withPad: " ", startingAt: 0)
            g.draw(
                Text(padded).font(.system(size: 13, design: .monospaced)).foregroundColor(Theme.neonDim),
                at: CGPoint(x: size.width / 2, y: titleY + 34)
            )
        }
    }
}
