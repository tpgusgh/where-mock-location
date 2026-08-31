// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "WhereMockLocationMac",
    platforms: [.macOS(.v14)],
    targets: [
        .executableTarget(
            name: "WhereMockLocationMac",
            path: "Sources/WhereMockLocationMac"
        ),
        .testTarget(
            name: "WhereMockLocationMacTests",
            dependencies: ["WhereMockLocationMac"],
            path: "Tests/WhereMockLocationMacTests"
        )
    ]
)
