// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "FaktelOnnxRuntime",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "FaktelOnnxRuntime", targets: ["FaktelOnnxRuntime"]),
    ],
    dependencies: [
        .package(url: "https://github.com/microsoft/onnxruntime-swift-package-manager", from: "1.20.0"),
    ],
    targets: [
        // Built locally with `./gradlew :faktel:assembleFaktelXCFramework`. When you consume a released
        // version, replace this with the `.binaryTarget(url:checksum:)` published on the GitHub release.
        .binaryTarget(
            name: "Faktel",
            path: "../../faktel/build/XCFrameworks/release/Faktel.xcframework"
        ),
        .target(
            name: "FaktelOnnxRuntime",
            dependencies: [
                "Faktel",
                .product(name: "onnxruntime", package: "onnxruntime-swift-package-manager"),
            ]
        ),
        .testTarget(name: "FaktelOnnxRuntimeTests", dependencies: ["FaktelOnnxRuntime"]),
    ]
)
