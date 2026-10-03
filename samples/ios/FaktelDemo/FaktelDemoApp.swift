import Faktel
import PhotosUI
import SwiftUI

private extension Color {
    static let bg = Color(red: 0.043, green: 0.059, blue: 0.102)
    static let card = Color(red: 0.078, green: 0.106, blue: 0.176)
    static let card2 = Color(red: 0.11, green: 0.145, blue: 0.25)
    static let accent = Color(red: 0.176, green: 0.831, blue: 0.749)
    static let good = Color(red: 0.204, green: 0.827, blue: 0.6)
    static let warn = Color(red: 0.984, green: 0.749, blue: 0.141)
    static let muted = Color(red: 0.58, green: 0.639, blue: 0.722)
}

@main
struct FaktelDemoApp: App {
    var body: some Scene {
        WindowGroup { RootView().preferredColorScheme(.dark) }
    }
}

enum Mode: String, CaseIterable { case selfie = "Selfie check", ktp = "KTP scan"
    var sample: String { self == .selfie ? "face_b" : "ktp_sample" }
}

@MainActor
final class Model: ObservableObject {
    @Published var mode = Mode.selfie
    @Published var photo: UIImage?
    @Published var face: (FaceAnalysis, Int)?
    @Published var ktp: (KtpScanResult, Int)?
    @Published var cardImage: UIImage?
    @Published var busy = false
    private var engine: FaktelEngine?

    func load(_ image: UIImage) {
        busy = true
        let mode = self.mode
        Task.detached(priority: .userInitiated) {
            // Model loading and inference stay off the main thread.
            let engine = try await MainActor.run { self.engine } ?? FaktelEngine()
            let rgb = image.toRgbImage()
            let f = mode == .selfie ? try? engine.analyzeSelfie(rgb) : nil
            let k = mode == .ktp ? try? engine.scanKtp(rgb) : nil
            let card = k?.0.card?.toUIImage()
            await MainActor.run {
                self.engine = engine
                self.photo = image; self.face = f; self.ktp = k; self.cardImage = card; self.busy = false
            }
        }
    }

    func loadSample() {
        photo = nil; face = nil; ktp = nil
        if let url = Bundle.main.url(forResource: mode.sample, withExtension: "jpg"), let img = UIImage(contentsOfFile: url.path) {
            load(img)
        }
    }
}

struct RootView: View {
    @StateObject private var m = Model()
    @State private var pick: PhotosPickerItem?

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    PhotoCard(m: m)
                    HStack(spacing: 10) {
                        Pill(text: "Sample", color: .accent) { m.loadSample() }
                        PhotosPicker(selection: $pick, matching: .images) { PillLabel(text: "Pick photo", color: .muted) }
                    }
                    if let (a, ms) = m.face { FaceResultView(a: a, ms: ms) }
                    if let (r, ms) = m.ktp { KtpResultView(r: r, ms: ms, card: m.cardImage) }
                }
                .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 24)
            }
            tabBar
        }
        .background(Color.bg.ignoresSafeArea())
        .onAppear { m.loadSample() }
        .onChange(of: pick) { _, item in
            guard let item else { return }
            Task {
                if let d = try? await item.loadTransferable(type: Data.self), let img = UIImage(data: d) { m.load(img) }
            }
        }
    }

    var header: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 10).fill(Color.accent).frame(width: 34, height: 34)
                .overlay(Text("F").font(.system(size: 18, weight: .black)).foregroundStyle(Color.bg))
            VStack(alignment: .leading, spacing: 1) {
                Text("Faktel").font(.system(size: 22, weight: .bold))
                Text("On-device face & e-KTP checks").font(.caption).foregroundStyle(Color.muted)
            }
        }
    }

    var tabBar: some View {
        HStack {
            ForEach(Mode.allCases, id: \.self) { t in
                Button { m.mode = t; m.loadSample() } label: {
                    Text(t.rawValue).fontWeight(.semibold).frame(maxWidth: .infinity).padding(.vertical, 12)
                        .foregroundStyle(m.mode == t ? Color.accent : Color.muted)
                        .background(m.mode == t ? Color.card2 : .clear, in: RoundedRectangle(cornerRadius: 14))
                }
            }
        }
        .padding(8).background(Color.card)
    }
}

struct Pill: View {
    let text: String, color: Color, action: () -> Void
    var body: some View { Button(action: action) { PillLabel(text: text, color: color) } }
}

struct PillLabel: View {
    let text: String, color: Color
    var body: some View {
        Text(text).font(.system(size: 13, weight: .semibold)).foregroundStyle(color)
            .padding(.horizontal, 16).padding(.vertical, 9)
            .overlay(Capsule().stroke(color.opacity(0.5), lineWidth: 1))
    }
}

struct PhotoCard: View {
    @ObservedObject var m: Model
    var body: some View {
        ZStack(alignment: .bottom) {
            if let img = m.photo {
                Image(uiImage: img).resizable().scaledToFit()
                    .overlay(GeometryReader { g in overlay(size: img.size, in: g.size) })
            } else {
                ProgressView().tint(.accent).frame(maxWidth: .infinity, minHeight: 320)
            }
            if m.busy { ProgressView().progressViewStyle(.linear).tint(.accent) }
        }
        .frame(maxWidth: .infinity)
        .background(Color.card)
        .clipShape(RoundedRectangle(cornerRadius: 24))
    }

    /// Faktel coordinates are in the pixels of the image given to it; `toRgbImage` may have downscaled, so map via ratios.
    func overlay(size: CGSize, in box: CGSize) -> some View {
        let k = min(1, 1800 / max(size.width, size.height))
        let s = box.width / (size.width * k)
        return ZStack {
            if let (a, _) = m.face {
                ForEach(0..<a.faces.count, id: \.self) { i in
                    let f = a.faces[i]
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(a.isAcceptable ? Color.good : Color.warn, lineWidth: 2.5)
                        .frame(width: f.box.width * s, height: f.box.height * s)
                        .position(x: f.box.centerX * s, y: f.box.centerY * s)
                    ForEach(0..<5, id: \.self) { j in
                        let p = f.landmarks.asList()[j]
                        Circle().fill(Color.accent).frame(width: 7, height: 7).position(x: p.x * s, y: p.y * s)
                    }
                }
            }
            if let (r, _) = m.ktp, let q = r.detection?.quad {
                Path { p in
                    p.move(to: CGPoint(x: q.topLeft.x * s, y: q.topLeft.y * s))
                    p.addLine(to: CGPoint(x: q.topRight.x * s, y: q.topRight.y * s))
                    p.addLine(to: CGPoint(x: q.bottomRight.x * s, y: q.bottomRight.y * s))
                    p.addLine(to: CGPoint(x: q.bottomLeft.x * s, y: q.bottomLeft.y * s))
                    p.closeSubpath()
                }.stroke(r.isAcceptable ? Color.good : Color.warn, lineWidth: 3)
            }
        }
    }
}

struct Verdict: View {
    let ok: Bool, title: String, ms: Int
    var body: some View {
        let c = ok ? Color.good : Color.warn
        HStack(spacing: 12) {
            Circle().fill(c).frame(width: 10, height: 10)
            Text(title).font(.system(size: 16, weight: .bold)).foregroundStyle(c)
            Spacer()
            Text("\(ms) ms").font(.caption).foregroundStyle(Color.muted)
        }
        .padding(16).background(c.opacity(0.12), in: RoundedRectangle(cornerRadius: 16))
    }
}

struct Metrics: View {
    let items: [(String, String)]
    var body: some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible())], spacing: 10) {
            ForEach(items.indices, id: \.self) { i in
                VStack(alignment: .leading, spacing: 4) {
                    Text(items[i].0).font(.system(size: 11)).foregroundStyle(Color.muted)
                    Text(items[i].1).font(.system(size: 18, weight: .semibold))
                }
                .frame(maxWidth: .infinity, alignment: .leading).padding(14)
                .background(Color.card, in: RoundedRectangle(cornerRadius: 16))
            }
        }
    }
}

struct IssueChips: View {
    let names: [String]
    var body: some View {
        HStack {
            ForEach(names, id: \.self) {
                Text($0).font(.system(size: 12, weight: .medium)).foregroundStyle(Color.warn)
                    .padding(.horizontal, 12).padding(.vertical, 6).background(Color.warn.opacity(0.12), in: Capsule())
            }
        }
    }
}

struct FaceResultView: View {
    let a: FaceAnalysis, ms: Int
    var body: some View {
        let q = a.quality, live = a.liveness
        Verdict(ok: a.isAcceptable, title: a.isAcceptable ? "Selfie accepted" : (a.face == nil ? "No face found" : "Needs another try"), ms: ms)
        Metrics(items: [
            ("Detector confidence", a.face.map { String(format: "%.0f%%", $0.score * 100) } ?? "-"),
            ("Liveness (real)", live.map { String(format: "%.0f%%", $0.realScore * 100) + ($0.isLive ? " ✓" : " ✗") } ?? "skipped"),
            ("Sharpness", q.map { String(format: "%.0f", $0.blurScore) } ?? "-"),
            ("Head pose", q.map { String(format: "yaw %.0f° roll %.0f°", $0.pose.yaw, $0.pose.roll) } ?? "-"),
        ])
        IssueChips(names: a.issues.map { $0.name })
    }
}

struct KtpResultView: View {
    let r: KtpScanResult, ms: Int, card: UIImage?
    var body: some View {
        Verdict(ok: r.isAcceptable, title: r.isAcceptable ? "KTP-like card accepted" : "Card needs a retake", ms: ms)
        if let card {
            Text("Rectified card (1011×638)").font(.caption).foregroundStyle(Color.muted)
            Image(uiImage: card).resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 16))
        }
        Metrics(items: [
            ("Sharpness", r.blurScore.map { String(format: "%.0f", $0.doubleValue) } ?? "-"),
            ("Glare", r.glareRatio.map { String(format: "%.1f%%", $0.doubleValue * 100) } ?? "-"),
            ("Card confidence", r.detection.map { String(format: "%.0f%%", $0.confidence * 100) } ?? "-"),
            ("Portrait", r.portrait != nil ? "found" : "missing"),
        ])
        IssueChips(names: r.issues.map { $0.name })
    }
}
