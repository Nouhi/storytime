import Foundation
import UIKit

enum GenerationState: Equatable {
    case idle
    case generating
    case complete
    case error(String)

    static func == (lhs: GenerationState, rhs: GenerationState) -> Bool {
        switch (lhs, rhs) {
        case (.idle, .idle), (.generating, .generating), (.complete, .complete):
            return true
        case (.error(let a), .error(let b)):
            return a == b
        default:
            return false
        }
    }
}

@MainActor
class GenerationViewModel: ObservableObject {
    @Published var state: GenerationState = .idle
    @Published var prompt = ""
    @Published var writingStyle = "standard"
    @Published var imageStyle = "watercolor"

    @Published var progress: Double = 0
    @Published var stepDetail = ""
    @Published var currentStep = ""

    @Published var storyPages: [StoryPage] = []
    @Published var storyId: String?
    @Published var epubUrl: String?
    @Published var hasImages = true

    @Published var currentPage = 1

    @Published var writingStyles: [StyleItem] = []
    @Published var imageStyles: [StyleItem] = []

    @Published var kidName: String

    // Family members for character picker
    @Published var familyMembers: [FamilyMemberResponse] = []
    @Published var selectedCharacterIds: Set<Int> = []
    private var hasCustomSelection = false

    // Sleep timer
    @Published var storiesCompletedInSession = 0
    @Published var shouldAutoActivateBedtime = false

    private let sseClient = SSEClient()
    private var generationTask: Task<Void, Never>?

    var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        let timeGreeting: String
        switch hour {
        case 5..<12: timeGreeting = "Good morning"
        case 12..<17: timeGreeting = "Good afternoon"
        default: timeGreeting = "Good evening"
        }

        if kidName.isEmpty {
            return "Welcome to Storytime!"
        } else {
            return "\(timeGreeting), \(kidName)!"
        }
    }

    init() {
        self.kidName = UserDefaults.standard.string(forKey: "kidName") ?? ""
    }

    let suggestions = [
        "A magical adventure in an enchanted forest",
        "A trip to outer space to visit friendly aliens",
        "A day at the beach with a talking dolphin",
        "A treasure hunt in a pirate ship",
    ]

    func toggleCharacter(_ id: Int) {
        hasCustomSelection = true
        if selectedCharacterIds.contains(id) {
            selectedCharacterIds.remove(id)
        } else {
            selectedCharacterIds.insert(id)
        }
    }

    func selectAllCharacters() {
        hasCustomSelection = false
        selectedCharacterIds = Set(familyMembers.map { $0.id })
    }

    func clearCharacterSelection() {
        hasCustomSelection = true
        selectedCharacterIds = []
    }

    func loadFamilyMembers() async {
        do {
            let members: [FamilyMemberResponse] = try await APIClient.shared.get("/api/family-members")
            familyMembers = members
            selectedCharacterIds = Set(members.map { $0.id })
        } catch {
            // Non-critical — generation still works without character selection
        }
    }

    func loadStyles() async {
        do {
            let response: StylesResponse = try await APIClient.shared.get("/api/styles")
            writingStyles = response.writingStyles
            imageStyles = response.imageStyles
            writingStyle = response.defaults.writingStyle
            imageStyle = response.defaults.imageStyle
        } catch {
            // Use hardcoded fallbacks
            writingStyles = [
                StyleItem(id: "standard", label: "Standard", emoji: "📖", description: "Classic bedtime story"),
                StyleItem(id: "rhyming", label: "Rhyming", emoji: "🎵", description: "Dr. Seuss-style verse"),
                StyleItem(id: "funny", label: "Funny", emoji: "😂", description: "Silly humor"),
                StyleItem(id: "bedtime", label: "Bedtime", emoji: "🌙", description: "Calm and dreamy"),
                StyleItem(id: "adventure", label: "Adventure", emoji: "⚔️", description: "Epic quests"),
            ]
            imageStyles = [
                StyleItem(id: "watercolor", label: "Watercolor", emoji: "🎨", description: "Soft watercolor"),
                StyleItem(id: "cartoon", label: "Cartoon", emoji: "🦸", description: "Bold cartoon"),
                StyleItem(id: "ghibli", label: "Ghibli", emoji: "🏔️", description: "Studio Ghibli-inspired"),
                StyleItem(id: "none", label: "No Images", emoji: "📝", description: "Text-only story"),
            ]
        }
    }

    func generate() async {
        guard !prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }

        state = .generating
        progress = 0
        stepDetail = "Starting..."
        currentStep = "pending"
        storyPages = []
        storyId = nil
        epubUrl = nil
        currentPage = 1

        do {
            // Start generation — only send characterIds if user made a custom selection
            let charIds: [Int]? = hasCustomSelection ? Array(selectedCharacterIds) : nil
            let request = GenerateRequest(
                prompt: prompt,
                writingStyle: writingStyle,
                imageStyle: imageStyle,
                characterIds: charIds
            )
            let response: GenerateResponse = try await APIClient.shared.post("/api/generate", body: request)
            storyId = response.storyId

            // Connect to SSE stream
            let streamURL = APIClient.shared.imageURL(for: "/api/generate/\(response.storyId)/stream")

            for await event in sseClient.connect(url: streamURL) {
                handleEvent(event)
                if event.type == "complete" || event.type == "error" {
                    break
                }
            }
        } catch {
            state = .error(error.localizedDescription)
        }
    }

    func cancelGeneration() {
        generationTask?.cancel()
        generationTask = nil
        state = .idle
    }

    func reset() {
        state = .idle
        prompt = ""
        progress = 0
        stepDetail = ""
        currentStep = ""
        storyPages = []
        storyId = nil
        epubUrl = nil
        currentPage = 1
        // Reset character selection to "all"
        hasCustomSelection = false
        selectedCharacterIds = Set(familyMembers.map { $0.id })
    }

    func saveEPUB() async {
        guard let storyId = storyId else { return }

        let path = "/api/generate/\(storyId)/epub"
        do {
            let data = try await APIClient.shared.downloadData(path)
            let filename = "storytime-\(storyId).epub"
            let fileURL = EPUBManager.shared.saveEPUB(data: data, filename: filename)
            EPUBManager.shared.openInBooks(fileURL: fileURL)
        } catch {
            // Silently handle - user can retry
        }
    }

    func savePDF() async {
        guard let storyId = storyId else { return }

        let path = "/api/generate/\(storyId)/pdf"
        do {
            let data = try await APIClient.shared.downloadData(path)
            let filename = "storytime-\(storyId).pdf"
            let documentsURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
            let fileURL = documentsURL.appendingPathComponent(filename)
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
            // Silently handle - user can retry
        }
    }

    func pageImageURL(page: Int) -> URL? {
        guard let storyId = storyId, hasImages else { return nil }
        return APIClient.shared.imageURL(for: "/api/generate/\(storyId)/pages?page=\(page)")
    }

    func prefetchAdjacentImages(currentPage: Int) {
        let pagesToPrefetch = [currentPage - 1, currentPage + 1].filter {
            $0 >= 1 && $0 <= storyPages.count
        }
        for page in pagesToPrefetch {
            guard let url = pageImageURL(page: page) else { continue }
            URLSession.shared.dataTask(with: url) { _, _, _ in }.resume()
        }
    }

    func refreshKidName() {
        kidName = UserDefaults.standard.string(forKey: "kidName") ?? ""
    }

    // MARK: - Private

    private func checkSleepTimer() {
        let target = UserDefaults.standard.integer(forKey: "sleepTimerStories")
        guard target > 0 else { return }
        if storiesCompletedInSession >= target {
            shouldAutoActivateBedtime = true
        }
    }

    private func handleEvent(_ event: GenerationEvent) {
        if let p = event.progress {
            progress = p
        }
        if let detail = event.detail {
            stepDetail = detail
        }
        if let step = event.step {
            currentStep = step
        }
        if let pages = event.storyPages {
            storyPages = pages
        }

        switch event.type {
        case "complete":
            epubUrl = event.epubUrl
            hasImages = event.hasImages ?? true
            if let pages = event.storyPages {
                storyPages = pages
            }
            state = .complete
            storiesCompletedInSession += 1
            checkSleepTimer()

        case "error":
            state = .error(event.message ?? "An unknown error occurred")

        default:
            break
        }
    }
}
