import BackgroundTasks
import Foundation
import QoriqchiCore
import SafariServices

/// Bazani yuklab, Safari bloklovchi qoidalarini yangilaydi. Ilova ochilganda (24 soatdan eski bo'lsa)
/// va iOS fonda ruxsat berganda ishlaydi.
enum BlocklistSync {
    static let taskID = "uz.apkqoriqchi.ios.refresh"

    /// Sideloadly bundle ID'ni o'zgartirishi mumkin — shuning uchun ilovaning haqiqiy ID'sidan olinadi.
    static var blockerID: String { (Bundle.main.bundleIdentifier ?? "uz.apkqoriqchi.ios") + ".ContentBlocker" }

    /// true — hamma ro'yxat yangilandi.
    static func run() async -> Bool {
        var ok = true
        var malware: [String] = []
        var phishing: [String] = []
        for feed in Blocklist.feeds {
            do {
                let domains = try await Blocklist.download(feed)
                try SharedStore.saveKeys(Blocklist.keys(domains), for: feed)
                if feed.kind == .malware { malware += domains } else { phishing += domains }
            } catch {
                ok = false
            }
        }
        // Bitta ro'yxat ishlamasa, eski (to'liq) qoidalar qoladi — faqat birinchi marta qisman bo'lsa ham yozamiz.
        if let url = SharedStore.blockerListURL, !malware.isEmpty || !phishing.isEmpty,
           ok || !FileManager.default.fileExists(atPath: url.path) {
            var seen = Set<String>()
            let ordered = (malware + phishing).filter { seen.insert($0).inserted }
            try? BlockerRules.json(domains: ordered).write(to: url, options: .atomic)
            try? await SFContentBlockerManager.reloadContentBlocker(withIdentifier: blockerID)
        }
        return ok
    }

    static func registerBackgroundTask() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskID, using: nil) { task in
            scheduleBackgroundTask()
            let work = Task {
                let ok = await run()
                task.setTaskCompleted(success: ok)
            }
            task.expirationHandler = { work.cancel() }
        }
    }

    static func scheduleBackgroundTask() {
        let request = BGAppRefreshTaskRequest(identifier: taskID)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 12 * 3600)
        try? BGTaskScheduler.shared.submit(request)
    }
}

@MainActor
final class Updater: ObservableObject {
    @Published private(set) var domains = 0
    @Published private(set) var updated: Date?
    @Published private(set) var running = false
    @Published private(set) var failed = false

    init() {
        refresh()
    }

    var stale: Bool {
        guard let updated else { return true }
        return Date().timeIntervalSince(updated) > 24 * 3600
    }

    func refresh() {
        let s = SharedStore.stats()
        domains = s.domains
        updated = s.updated
    }

    func updateIfStale() async {
        if stale { await update() }
    }

    func update() async {
        guard !running else { return }
        running = true
        failed = false
        let ok = await BlocklistSync.run()
        refresh()
        running = false
        failed = !ok
    }
}
