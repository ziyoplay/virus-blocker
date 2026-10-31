import SwiftUI

@main
struct QoriqchiApp: App {
    @Environment(\.scenePhase) private var phase
    @StateObject private var updater = Updater()

    init() {
        BlocklistSync.registerBackgroundTask()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(updater)
                .tint(.blue)
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
                    .tabItem { Label("Himoya", systemImage: "shield.lefthalf.filled") }
                LinkCheckView()
                    .tabItem { Label("Havola", systemImage: "link") }
                SecurityView()
                    .tabItem { Label("Tekshiruv", systemImage: "checklist") }
            }
            if showIntro {
                IntroView {
                    withAnimation(.easeOut(duration: 0.35)) { showIntro = false }
                }
                .transition(.opacity.combined(with: .scale(scale: 1.08)))
                .zIndex(1)
            }
        }
    }
}
