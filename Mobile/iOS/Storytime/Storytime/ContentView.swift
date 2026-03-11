import SwiftUI

struct ContentView: View {
    @EnvironmentObject var localeManager: LocaleManager

    var body: some View {
        TabView {
            GenerateView()
                .tabItem {
                    Label(localeManager.localized("tab_create"), systemImage: "sparkles")
                }

            HistoryListView()
                .tabItem {
                    Label(localeManager.localized("tab_history"), systemImage: "clock")
                }

            SettingsView()
                .tabItem {
                    Label(localeManager.localized("tab_settings"), systemImage: "gear")
                }
        }
        .tint(Color.stPrimary)
    }
}
