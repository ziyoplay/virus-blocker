// swift-tools-version:5.7
import PackageDescription

// Ilova va kengaytmalar (Safari bloklovchi, SMS filtri) uchun umumiy mantiq.
// UIKit'siz: testlar macOS/Linux'da `swift test` bilan ishlaydi.
let package = Package(
    name: "QoriqchiCore",
    platforms: [.iOS(.v16), .macOS(.v12)],
    products: [
        .library(name: "QoriqchiCore", targets: ["QoriqchiCore"]),
    ],
    targets: [
        .target(name: "QoriqchiCore"),
        .testTarget(name: "QoriqchiCoreTests", dependencies: ["QoriqchiCore"]),
    ]
)
