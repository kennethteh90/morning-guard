// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "MorningGuardCore",
    platforms: [
        .iOS(.v17),
        .macOS(.v13),
    ],
    products: [
        .library(
            name: "MorningGuardCore",
            targets: ["MorningGuardCore"]
        ),
    ],
    targets: [
        .target(
            name: "MorningGuardCore"
        ),
        .testTarget(
            name: "MorningGuardCoreTests",
            dependencies: ["MorningGuardCore"]
        ),
    ]
)
