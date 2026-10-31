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
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}
