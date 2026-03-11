import SwiftUI

// MARK: - Storytime Color Tokens

extension Color {
    // Primary — warm purple
    static let stPrimary = Color(hex: 0x8B5CF6)
    static let stPrimaryLight = Color(hex: 0xEDE9FE)

    // Secondary — warm amber
    static let stSecondary = Color(hex: 0xF59E0B)
    static let stSecondaryLight = Color(hex: 0xFEF3C7)

    // Accent — soft teal
    static let stAccent = Color(hex: 0x14B8A6)

    // Surfaces — warm off-whites
    static let stSurface = Color(hex: 0xFEFBF6)
    static let stSurfaceCard = Color(hex: 0xFFF8F0)

    // Text
    static let stTextPrimary = Color(hex: 0x1E1B4B)
    static let stTextSecondary = Color(hex: 0x6B7280)
    static let stTextTertiary = Color(hex: 0x9CA3AF)

    // Gradients
    static let stGradientStart = Color(hex: 0x8B5CF6)
    static let stGradientEnd = Color(hex: 0x6366F1)

    // Bedtime
    static let stBedtime = Color(hex: 0x4338CA)

    // Convenience initializer from hex
    init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: alpha
        )
    }
}

// MARK: - Storytime Gradients

extension LinearGradient {
    static let stPrimaryButton = LinearGradient(
        colors: [Color.stGradientStart, Color.stGradientEnd],
        startPoint: .leading,
        endPoint: .trailing
    )
}

// MARK: - View Modifiers

struct StCardModifier: ViewModifier {
    var padding: CGFloat = 16
    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background(Color.stSurfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .shadow(color: .black.opacity(0.04), radius: 8, y: 2)
    }
}

struct StGradientButtonModifier: ViewModifier {
    var isDisabled: Bool = false
    func body(content: Content) -> some View {
        content
            .font(.headline)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(isDisabled ? AnyShapeStyle(Color.gray.opacity(0.4)) : AnyShapeStyle(LinearGradient.stPrimaryButton))
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .shadow(color: isDisabled ? .clear : Color.stPrimary.opacity(0.3), radius: 8, y: 4)
    }
}

struct StChipModifier: ViewModifier {
    var isSelected: Bool
    func body(content: Content) -> some View {
        content
            .font(.caption)
            .padding(.horizontal, 14)
            .padding(.vertical, 9)
            .background(isSelected ? Color.stPrimaryLight : Color.stSurfaceCard)
            .foregroundStyle(isSelected ? Color.stPrimary : Color.stTextSecondary)
            .clipShape(Capsule())
            .overlay(
                Capsule()
                    .stroke(isSelected ? Color.stPrimary.opacity(0.3) : Color.clear, lineWidth: 1)
            )
    }
}

struct StSectionHeaderModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(Color.stTextSecondary)
    }
}

extension View {
    func stCard(padding: CGFloat = 16) -> some View {
        modifier(StCardModifier(padding: padding))
    }

    func stGradientButton(isDisabled: Bool = false) -> some View {
        modifier(StGradientButtonModifier(isDisabled: isDisabled))
    }

    func stChip(isSelected: Bool) -> some View {
        modifier(StChipModifier(isSelected: isSelected))
    }

    func stSectionHeader() -> some View {
        modifier(StSectionHeaderModifier())
    }
}
