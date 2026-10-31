import SwiftUI

@main
struct QoriqchiApp: App {
    @Environment(\.scenePhase) private var phase
    @StateObject private var updater = Updater()

    init() {
        BlocklistSync.registerBackgroundTask()
        Theme.configureAppearance()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(updater)
                .flavorStyle()
                .task { await updater.updateIfStale() }
        }
        .onChange(of: phase) { p in
            if p == .background { BlocklistSync.scheduleBackgroundTask() }
        }
    }
}

struct RootView: View {
    @State private var showIntro = true

    var body: some View {
        ZStack {
            TabView {
                ProtectionView()
                    .tabItem { Label(Voice.tabProtection, systemImage: "shield.lefthalf.filled") }
                LinkCheckView()
                    .tabItem { Label(Voice.tabLink, systemImage: Flavor.hacker ? "terminal" : "link") }
                SecurityView()
                    .tabItem { Label(Voice.tabAudit, systemImage: "checklist") }
            }
            if showIntro {
                Group {
                    if Flavor.hacker {
                        TerminalIntroView(onDone: hideIntro)
                    } else {
                        IntroView(onDone: hideIntro)
                    }
                }
                .transition(.opacity.combined(with: .scale(scale: 1.08)))
                .zIndex(1)
            }
        }
    }

    private func hideIntro() {
        withAnimation(.easeOut(duration: 0.35)) { showIntro = false }
    }
}
