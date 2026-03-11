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
    @Published var lesson = "none"
    @Published var customWritingStyle = ""
    @Published var customImageStyle = ""
    @Published var customLesson = ""
    @Published var language = "en" {
        didSet { LocaleManager.shared.languageCode = language }
    }
    @Published var isBedtimeStory = false

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
    @Published var lessons: [StyleItem] = []
    @Published var languages: [StyleItem] = []

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
        case 5..<12: timeGreeting = LocaleManager.shared.localized("greeting_morning")
        case 12..<17: timeGreeting = LocaleManager.shared.localized("greeting_afternoon")
        default: timeGreeting = LocaleManager.shared.localized("greeting_evening")
        }

        if kidName.isEmpty {
            return LocaleManager.shared.localized("greeting_welcome")
        } else {
            return LocaleManager.shared.localized("greeting_personalized", timeGreeting, kidName)
        }
    }

    init() {
        self.kidName = UserDefaults.standard.string(forKey: "kidName") ?? ""
        self.language = LocaleManager.shared.languageCode
    }

    var suggestions: [String] {
        [
            LocaleManager.shared.localized("suggestion_1"),
            LocaleManager.shared.localized("suggestion_2"),
            LocaleManager.shared.localized("suggestion_3"),
            LocaleManager.shared.localized("suggestion_4"),
        ]
    }

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
        let lm = LocaleManager.shared
        do {
            let response: StylesResponse = try await APIClient.shared.get("/api/styles")
            writingStyles = response.writingStyles.map { lm.localizedStyleItem($0) }
            imageStyles = response.imageStyles.map { lm.localizedStyleItem($0) }
            lessons = (response.lessons ?? []).map { lm.localizedLessonItem($0) }
            languages = response.languages ?? Self.fallbackLanguages
            writingStyle = response.defaults.writingStyle
            imageStyle = response.defaults.imageStyle
            lesson = response.defaults.lesson ?? "none"
            language = response.defaults.language ?? LocaleManager.shared.languageCode
        } catch {
            // Use hardcoded fallbacks mapped through localization
            writingStyles = [
                StyleItem(id: "standard", label: "Standard", emoji: "📖", description: "Classic bedtime story narration"),
                StyleItem(id: "rhyming", label: "Rhyming", emoji: "🎵", description: "Dr. Seuss-style rhyming verse"),
                StyleItem(id: "funny", label: "Funny", emoji: "😂", description: "Silly humor and unexpected twists"),
                StyleItem(id: "sound-effects", label: "Sound Effects", emoji: "💥", description: "Onomatopoeia and interactive sounds"),
                StyleItem(id: "repetitive", label: "Repetitive", emoji: "🔁", description: "Cumulative story with repeating phrases"),
                StyleItem(id: "bedtime", label: "Bedtime", emoji: "🌙", description: "Extra calm and dreamy for sleepy time"),
                StyleItem(id: "adventure", label: "Adventure", emoji: "⚔️", description: "Epic quests and brave heroes"),
            ].map { lm.localizedStyleItem($0) }
            imageStyles = [
                StyleItem(id: "watercolor", label: "Watercolor", emoji: "🎨", description: "Soft, dreamy watercolor paintings"),
                StyleItem(id: "fantasy", label: "Fantasy", emoji: "🧙", description: "Rich, magical fantasy art"),
                StyleItem(id: "realistic", label: "Realistic", emoji: "📷", description: "Photo-realistic digital art"),
                StyleItem(id: "cartoon", label: "Cartoon", emoji: "🦸", description: "Bold, colorful cartoon style"),
                StyleItem(id: "classic-storybook", label: "Classic Storybook", emoji: "📚", description: "Vintage children's book illustrations"),
                StyleItem(id: "anime", label: "Anime", emoji: "✨", description: "Japanese anime-inspired art"),
                StyleItem(id: "ghibli", label: "Ghibli", emoji: "🏔️", description: "Studio Ghibli-inspired art"),
                StyleItem(id: "chibi", label: "Chibi", emoji: "🎀", description: "Cute, super-deformed chibi art"),
                StyleItem(id: "papercraft", label: "Papercraft", emoji: "✂️", description: "Cut-paper collage style"),
                StyleItem(id: "pixel", label: "Pixel Art", emoji: "👾", description: "Retro pixel art style"),
                StyleItem(id: "minimalist", label: "Minimalist", emoji: "⚪", description: "Clean, simple geometric shapes"),
                StyleItem(id: "crayon", label: "Crayon", emoji: "🖍️", description: "Child-like crayon and colored pencil"),
                StyleItem(id: "pop-art", label: "Pop Art", emoji: "🎪", description: "Bold pop art with halftone dots"),
                StyleItem(id: "oil-painting", label: "Oil Painting", emoji: "🖼️", description: "Rich, textured oil painting style"),
                StyleItem(id: "none", label: "No Images", emoji: "📝", description: "Text-only story, no illustrations"),
            ].map { lm.localizedStyleItem($0) }
            lessons = [
                StyleItem(id: "none", label: "None", emoji: "📖", description: "No specific lesson — just a fun story"),
                StyleItem(id: "sharing", label: "Sharing", emoji: "🤝", description: "Learning to share with others"),
                StyleItem(id: "bravery", label: "Bravery", emoji: "🦁", description: "Finding courage in tough moments"),
                StyleItem(id: "kindness", label: "Kindness", emoji: "💛", description: "Being kind and caring to others"),
                StyleItem(id: "patience", label: "Patience", emoji: "🐢", description: "Learning to wait and be patient"),
                StyleItem(id: "honesty", label: "Honesty", emoji: "⭐", description: "The importance of telling the truth"),
                StyleItem(id: "gratitude", label: "Gratitude", emoji: "🙏", description: "Appreciating what you have"),
                StyleItem(id: "teamwork", label: "Teamwork", emoji: "🧩", description: "Working together to achieve goals"),
                StyleItem(id: "empathy", label: "Empathy", emoji: "🫂", description: "Understanding others' feelings"),
                StyleItem(id: "perseverance", label: "Perseverance", emoji: "🏔️", description: "Not giving up when things are hard"),
            ].map { lm.localizedLessonItem($0) }
            languages = Self.fallbackLanguages
        }
    }

    static let fallbackLanguages: [StyleItem] = [
        StyleItem(id: "en", label: "English", emoji: "🇺🇸", description: "English"),
        StyleItem(id: "es", label: "Spanish", emoji: "🇪🇸", description: "Español"),
        StyleItem(id: "fr", label: "French", emoji: "🇫🇷", description: "Français"),
        StyleItem(id: "ar", label: "Arabic", emoji: "🇸🇦", description: "العربية"),
        StyleItem(id: "de", label: "German", emoji: "🇩🇪", description: "Deutsch"),
        StyleItem(id: "zh", label: "Chinese", emoji: "🇨🇳", description: "中文"),
        StyleItem(id: "pt", label: "Portuguese", emoji: "🇧🇷", description: "Português"),
        StyleItem(id: "hi", label: "Hindi", emoji: "🇮🇳", description: "हिन्दी"),
        StyleItem(id: "ja", label: "Japanese", emoji: "🇯🇵", description: "日本語"),
        StyleItem(id: "ko", label: "Korean", emoji: "🇰🇷", description: "한국어"),
        StyleItem(id: "it", label: "Italian", emoji: "🇮🇹", description: "Italiano"),
        StyleItem(id: "nl", label: "Dutch", emoji: "🇳🇱", description: "Nederlands"),
        StyleItem(id: "ru", label: "Russian", emoji: "🇷🇺", description: "Русский"),
        StyleItem(id: "tr", label: "Turkish", emoji: "🇹🇷", description: "Türkçe"),
    ]

    func validateCustomInputs() -> String? {
        if writingStyle == "custom" {
            let text = customWritingStyle.trimmingCharacters(in: .whitespacesAndNewlines)
            if text.isEmpty {
                return LocaleManager.shared.localized("validation_custom_writing_empty")
            }
            if text.count > 500 {
                return LocaleManager.shared.localized("validation_custom_writing_long")
            }
        }
        if imageStyle == "custom" {
            let text = customImageStyle.trimmingCharacters(in: .whitespacesAndNewlines)
            if text.isEmpty {
                return LocaleManager.shared.localized("validation_custom_image_empty")
            }
            if text.count > 500 {
                return LocaleManager.shared.localized("validation_custom_image_long")
            }
        }
        if lesson == "custom" {
            let text = customLesson.trimmingCharacters(in: .whitespacesAndNewlines)
            if text.isEmpty {
                return LocaleManager.shared.localized("validation_custom_lesson_empty")
            }
            if text.count > 500 {
                return LocaleManager.shared.localized("validation_custom_lesson_long")
            }
        }
        return nil
    }

    func generate() async {
        guard !prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }

        if let validationError = validateCustomInputs() {
            state = .error(validationError)
            return
        }

        state = .generating
        progress = 0
        stepDetail = LocaleManager.shared.localized("progress_starting")
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
                lesson: lesson == "none" ? nil : lesson,
                characterIds: charIds,
                customWritingStyle: writingStyle == "custom" ? customWritingStyle : nil,
                customImageStyle: imageStyle == "custom" ? customImageStyle : nil,
                customLesson: lesson == "custom" ? customLesson : nil,
                bedtimeStory: isBedtimeStory ? true : nil,
                language: language != "en" ? language : nil
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
        isBedtimeStory = false
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
        if let step = event.step {
            currentStep = step
            switch step {
            case "generating-story":
                stepDetail = LocaleManager.shared.localized("progress_writing")
            case "generating-images":
                stepDetail = LocaleManager.shared.localized("progress_painting")
            case "assembling-ebook":
                stepDetail = LocaleManager.shared.localized("progress_assembling")
            default:
                if let detail = event.detail {
                    stepDetail = detail
                }
            }
        } else if let detail = event.detail {
            stepDetail = detail
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
            state = .error(event.message ?? LocaleManager.shared.localized("error_unknown"))

        default:
            break
        }
    }
}
