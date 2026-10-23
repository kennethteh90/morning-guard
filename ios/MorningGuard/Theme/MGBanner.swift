import SwiftUI

struct MGBannerPayload: Equatable {
    var text: String
    var showsUndo: Bool = false

    static func message(_ text: String) -> MGBannerPayload {
        MGBannerPayload(text: text, showsUndo: false)
    }

    static func undo(_ text: String) -> MGBannerPayload {
        MGBannerPayload(text: text, showsUndo: true)
    }
}

struct MGBannerOverlay: View {
    let payload: MGBannerPayload?
    var onUndo: (() -> Void)?

    var body: some View {
        VStack {
            if let payload {
                HStack(spacing: 12) {
                    Image(systemName: payload.showsUndo ? "trash" : "checkmark.circle.fill")
                        .foregroundStyle(payload.showsUndo ? Color.secondary : MGTheme.accent)
                    Text(payload.text)
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                        .lineLimit(2)
                    Spacer(minLength: 8)
                    if payload.showsUndo, let onUndo {
                        Button("Undo", action: onUndo)
                            .font(.subheadline.bold())
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(.regularMaterial)
                        .shadow(color: .black.opacity(0.15), radius: 10, y: 4)
                }
                .padding(.horizontal, MGTheme.standardPadding)
                .padding(.top, 8)
                .transition(.move(edge: .top).combined(with: .opacity))
            }
            Spacer()
        }
        .animation(.spring(duration: 0.35), value: payload)
        .allowsHitTesting(payload != nil)
    }
}

enum MGBanner {
    @MainActor
    static func present(
        _ payload: MGBannerPayload,
        binding: Binding<MGBannerPayload?>,
        duration: TimeInterval? = nil
    ) {
        binding.wrappedValue = payload
        let seconds = duration ?? (payload.showsUndo ? 5 : 2.5)
        Task {
            try? await Task.sleep(for: .seconds(seconds))
            await MainActor.run {
                if binding.wrappedValue == payload {
                    binding.wrappedValue = nil
                }
            }
        }
    }
}
