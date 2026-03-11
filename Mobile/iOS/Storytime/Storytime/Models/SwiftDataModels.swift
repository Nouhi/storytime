import Foundation
import SwiftData

@Model
class CachedSettings {
    var kidName: String
    var kidGender: String
    var readingLevel: String
    var kidPhotoPath: String
    var anthropicApiKey: String
    var googleAiApiKey: String
    var updatedAt: Date
    var serverBaseURL: String

    init(
        kidName: String = "",
        kidGender: String = "",
        readingLevel: String = "early-reader",
        kidPhotoPath: String = "",
        anthropicApiKey: String = "",
        googleAiApiKey: String = "",
        updatedAt: Date = .now,
        serverBaseURL: String = "http://localhost:3002"
    ) {
        self.kidName = kidName
        self.kidGender = kidGender
        self.readingLevel = readingLevel
        self.kidPhotoPath = kidPhotoPath
        self.anthropicApiKey = anthropicApiKey
        self.googleAiApiKey = googleAiApiKey
        self.updatedAt = updatedAt
        self.serverBaseURL = serverBaseURL
    }

    func update(from response: SettingsResponse) {
        kidName = response.kidName
        kidGender = response.kidGender
        readingLevel = response.readingLevel
        kidPhotoPath = response.kidPhotoPath
        anthropicApiKey = response.anthropicApiKey
        googleAiApiKey = response.googleAiApiKey
        updatedAt = .now
    }
}

@Model
class CachedStory {
    @Attribute(.unique) var serverID: Int
    var title: String
    var prompt: String
    var createdAt: Date
    var claudeInputTokens: Int
    var claudeOutputTokens: Int
    var geminiImageCount: Int
    var claudeCost: Double
    var geminiCost: Double
    var totalCost: Double
    var epubLocalPath: String?

    init(
        serverID: Int,
        title: String,
        prompt: String,
        createdAt: Date,
        claudeInputTokens: Int = 0,
        claudeOutputTokens: Int = 0,
        geminiImageCount: Int = 0,
        claudeCost: Double = 0,
        geminiCost: Double = 0,
        totalCost: Double = 0,
        epubLocalPath: String? = nil
    ) {
        self.serverID = serverID
        self.title = title
        self.prompt = prompt
        self.createdAt = createdAt
        self.claudeInputTokens = claudeInputTokens
        self.claudeOutputTokens = claudeOutputTokens
        self.geminiImageCount = geminiImageCount
        self.claudeCost = claudeCost
        self.geminiCost = geminiCost
        self.totalCost = totalCost
        self.epubLocalPath = epubLocalPath
    }

    func update(from entry: StoryHistoryEntry) {
        title = entry.title
        prompt = entry.prompt
        claudeInputTokens = entry.claudeInputTokens
        claudeOutputTokens = entry.claudeOutputTokens
        geminiImageCount = entry.geminiImageCount
        claudeCost = entry.claudeCost
        geminiCost = entry.geminiCost
        totalCost = entry.totalCost
    }
}
