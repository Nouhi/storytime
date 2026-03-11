import Foundation
import UIKit

@MainActor
class HistoryViewModel: ObservableObject {
    @Published var stories: [StoryHistoryEntry] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    // Detail view state
    @Published var selectedStory: StoryHistoryEntry?
    @Published var storyPages: [StoryPage] = []
    @Published var storyTitle = ""
    @Published var isLoadingDetail = false
    @Published var currentPage = 1

    func loadHistory() async {
        isLoading = true
        errorMessage = nil

        do {
            stories = try await APIClient.shared.get("/api/story-history")
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }

    func loadStoryDetail(id: Int) async {
        isLoadingDetail = true
        currentPage = 1

        do {
            let data: StoryDataResponse = try await APIClient.shared.get("/api/story-history/\(id)/story-data")
            storyPages = data.pages
            storyTitle = data.title
        } catch {
            storyPages = []
            storyTitle = "Error loading story"
        }

        isLoadingDetail = false
    }

    func pageImageURL(storyId: Int, page: Int) -> URL {
        APIClient.shared.imageURL(for: "/api/story-history/\(storyId)/pages?page=\(page)")
    }

    func downloadEPUB(storyId: Int, title: String) async {
        do {
            let data = try await APIClient.shared.downloadData("/api/story-history/\(storyId)/download")
            let safeName = title
                .replacingOccurrences(of: "[^a-zA-Z0-9\\s-]", with: "", options: .regularExpression)
                .replacingOccurrences(of: "\\s+", with: "-", options: .regularExpression)
                .prefix(60)
            let filename = "\(safeName.isEmpty ? "story" : safeName).epub"
            let fileURL = EPUBManager.shared.saveEPUB(data: data, filename: String(filename))
            EPUBManager.shared.openInBooks(fileURL: fileURL)
        } catch {
            // Silently handle
        }
    }

    func downloadPDF(storyId: Int, title: String) async {
        do {
            let data = try await APIClient.shared.downloadData("/api/story-history/\(storyId)/pdf")
            let safeName = title
                .replacingOccurrences(of: "[^a-zA-Z0-9\\s-]", with: "", options: .regularExpression)
                .replacingOccurrences(of: "\\s+", with: "-", options: .regularExpression)
                .prefix(60)
            let filename = "\(safeName.isEmpty ? "story" : safeName).pdf"
            let documentsURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
            let fileURL = documentsURL.appendingPathComponent(String(filename))
            try data.write(to: fileURL)

            // Share via activity controller
            await MainActor.run {
                guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                      let window = scene.windows.first,
                      let rootVC = window.rootViewController else { return }

                let activityVC = UIActivityViewController(activityItems: [fileURL], applicationActivities: nil)
                if let popover = activityVC.popoverPresentationController {
                    popover.sourceView = window
                    popover.sourceRect = CGRect(x: window.bounds.midX, y: window.bounds.maxY - 50, width: 0, height: 0)
                }
                rootVC.present(activityVC, animated: true)
            }
        } catch {
            // Silently handle
        }
    }

    func deleteStory(id: Int) async {
        do {
            try await APIClient.shared.delete("/api/story-history/\(id)")
            stories.removeAll { $0.id == id }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func prefetchAdjacentImages(storyId: Int, currentPage: Int, hasImages: Bool) {
        guard hasImages else { return }
        let pagesToPrefetch = [currentPage - 1, currentPage + 1].filter {
            $0 >= 1 && $0 <= storyPages.count
        }
        for page in pagesToPrefetch {
            let url = pageImageURL(storyId: storyId, page: page)
            URLSession.shared.dataTask(with: url) { _, _, _ in }.resume()
        }
    }

    var totalCost: Double {
        stories.reduce(0) { $0 + $1.totalCost }
    }

    static func formatCost(_ cost: Double) -> String {
        if cost > 0 && cost < 0.01 {
            return "<$0.01"
        }
        return String(format: "$%.2f", cost)
    }
}
