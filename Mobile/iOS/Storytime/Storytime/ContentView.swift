import SwiftUI

struct ContentView: View {
    var body: some View {
        TabView {
            GenerateView()
                .tabItem {
                    Label("Create", systemImage: "sparkles")
                }

            HistoryListView()
                .tabItem {
                    Label("History", systemImage: "clock")
                }

            SettingsView()
                .tabItem {
                    Label("Settings", systemImage: "gear")
                }
        }
        .tint(Color.stPrimary)
    }
}
