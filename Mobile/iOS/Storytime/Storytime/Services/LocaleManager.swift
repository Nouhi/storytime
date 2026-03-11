import SwiftUI
import Foundation

@MainActor
class LocaleManager: ObservableObject {
    static let shared = LocaleManager()

    @Published var locale: Locale
    @Published private(set) var bundle: Bundle

    /// The ISO 639-1 language code (e.g., "en", "ar", "zh")
    @Published var languageCode: String {
        didSet {
            UserDefaults.standard.set(languageCode, forKey: "appLanguage")
            updateBundle()
            locale = Locale(identifier: languageCode)
        }
    }

    /// Whether the current language is RTL (Arabic)
    var isRTL: Bool {
        Locale.characterDirection(forLanguage: languageCode) == .rightToLeft
    }

    private init() {
        let saved = UserDefaults.standard.string(forKey: "appLanguage") ?? "en"
        self.languageCode = saved
        self.locale = Locale(identifier: saved)
        self.bundle = Bundle.main
        updateBundle()
    }

    private func updateBundle() {
        // iOS uses "zh-Hans" for Simplified Chinese lproj directories
        let lprojName = languageCode == "zh" ? "zh-Hans" : languageCode

        if let path = Bundle.main.path(forResource: lprojName, ofType: "lproj"),
           let localizedBundle = Bundle(path: path) {
            self.bundle = localizedBundle
        } else if let path = Bundle.main.path(forResource: "en", ofType: "lproj"),
                  let enBundle = Bundle(path: path) {
            self.bundle = enBundle
        } else {
            self.bundle = Bundle.main
        }
    }

    /// Look up a localized string by key.
    func localized(_ key: String) -> String {
        bundle.localizedString(forKey: key, value: key, table: nil)
    }

    /// Look up a localized string with format arguments.
    func localized(_ key: String, _ arguments: CVarArg...) -> String {
        let format = bundle.localizedString(forKey: key, value: key, table: nil)
        return String(format: format, arguments: arguments)
    }

    /// Map a StyleItem's label and description to localized versions using its ID.
    func localizedStyleItem(_ item: StyleItem) -> StyleItem {
        let labelKey = "style_\(item.id.replacingOccurrences(of: "-", with: "_"))"
        let descKey = "style_desc_\(item.id.replacingOccurrences(of: "-", with: "_"))"

        let localizedLabel = localized(labelKey)
        let localizedDesc = localized(descKey)

        return StyleItem(
            id: item.id,
            label: localizedLabel == labelKey ? item.label : localizedLabel,
            emoji: item.emoji,
            description: localizedDesc == descKey ? item.description : localizedDesc
        )
    }

    /// Map a lesson StyleItem's label and description to localized versions.
    func localizedLessonItem(_ item: StyleItem) -> StyleItem {
        let labelKey = "lesson_\(item.id.replacingOccurrences(of: "-", with: "_"))"
        let descKey = "lesson_desc_\(item.id.replacingOccurrences(of: "-", with: "_"))"

        let localizedLabel = localized(labelKey)
        let localizedDesc = localized(descKey)

        return StyleItem(
            id: item.id,
            label: localizedLabel == labelKey ? item.label : localizedLabel,
            emoji: item.emoji,
            description: localizedDesc == descKey ? item.description : localizedDesc
        )
    }
}
