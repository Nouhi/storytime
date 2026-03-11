import SwiftUI
import SwiftData

@main
struct StorytimeApp: App {
    @StateObject private var localeManager = LocaleManager.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(localeManager)
                .environment(\.locale, localeManager.locale)
                .environment(\.layoutDirection,
                    localeManager.isRTL ? .rightToLeft : .leftToRight)
        }
        .modelContainer(for: [CachedSettings.self, CachedStory.self])
    }
}
