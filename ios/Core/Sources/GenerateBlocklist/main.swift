import Foundation
import QoriqchiCore

// Ilova yig'ilayotganda (CI) bazani yuklab, kengaytmalar ichiga joylanadigan fayllarni yaratadi:
//   blockerList.json — Safari bloklovchi qoidalari
//   <ro'yxat>.bin    — SMS filtri va havola tekshiruvi uchun xeshlar
// Bepul Apple ID'da App Group ishlamaydi, shuning uchun kengaytmalar bazani o'z ichida olib yuradi.
// Ishlatish: swift run GenerateBlocklist <papka>

let out = URL(fileURLWithPath: CommandLine.arguments.dropFirst().first ?? "Generated")
try FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)

var malware: [String] = []
var phishing: [String] = []
for feed in Blocklist.feeds {
    let domains = try await Blocklist.download(feed)
    try Blocklist.encode(Blocklist.keys(domains)).write(to: out.appendingPathComponent("\(feed.id).bin"))
    if feed.kind == .malware { malware += domains } else { phishing += domains }
    print("\(feed.id): \(domains.count) domen")
}

var seen = Set<String>()
let ordered = (malware + phishing).filter { seen.insert($0).inserted }
try BlockerRules.json(domains: ordered).write(to: out.appendingPathComponent("blockerList.json"))
print("blockerList.json: \(min(ordered.count, BlockerRules.maxDomainRules)) / \(ordered.count) domen")
