import Foundation

// MARK: - Settings

struct SettingsResponse: Codable {
    let id: Int
    let kidName: String
    let kidGender: String
    let readingLevel: String
    let kidPhotoPath: String
    let anthropicApiKey: String
    let googleAiApiKey: String
    let updatedAt: String
}

struct SettingsUpdateRequest: Codable {
    let kidName: String?
    let kidGender: String?
    let readingLevel: String?
    let anthropicApiKey: String?
    let googleAiApiKey: String?
}

// MARK: - Styles

struct StylesResponse: Codable {
    let writingStyles: [StyleItem]
    let imageStyles: [StyleItem]
    let lessons: [StyleItem]?
    let defaults: StyleDefaults
}

struct StyleItem: Codable, Identifiable {
    let id: String
    let label: String
    let emoji: String
    let description: String
}

struct StyleDefaults: Codable {
    let writingStyle: String
    let imageStyle: String
    let lesson: String?
}

// MARK: - Story Generation

struct GenerateRequest: Codable {
    let prompt: String
    let writingStyle: String
    let imageStyle: String
    let lesson: String?
    let characterIds: [Int]?
}

struct GenerateResponse: Codable {
    let storyId: String
}

struct StoryPage: Codable, Identifiable {
    let page: Int
    let text: String
    let imageDescription: String

    var id: Int { page }
}

struct GenerationEvent: Codable {
    let type: String // "progress", "complete", "error"
    let step: String?
    let detail: String?
    let progress: Double?
    let epubUrl: String?
    let storyId: String?
    let storyPages: [StoryPage]?
    let hasImages: Bool?
    let message: String?
}

// MARK: - Story History

struct StoryHistoryEntry: Codable, Identifiable {
    let id: Int
    let title: String
    let prompt: String
    let createdAt: String
    let claudeInputTokens: Int
    let claudeOutputTokens: Int
    let geminiImageCount: Int
    let claudeCost: Double
    let geminiCost: Double
    let totalCost: Double
    let pdfPath: String
}

struct StoryDataResponse: Codable {
    let pages: [StoryPage]
    let title: String
}

// MARK: - Family Members

struct FamilyMemberResponse: Codable, Identifiable {
    let id: Int
    let name: String
    let role: String
    let photoPath: String?
    let description: String?
}

struct FamilyMemberCreateRequest: Codable {
    let name: String
    let role: String
    let description: String?
}

struct FamilyMemberUpdateRequest: Codable {
    let name: String?
    let role: String?
    let description: String?
}

// MARK: - Upload

struct UploadResponse: Codable {
    let photoPath: String
}
