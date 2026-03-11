import Foundation
import SwiftUI
import PhotosUI

@MainActor
class SettingsViewModel: ObservableObject {
    @Published var kidName = ""
    @Published var kidGender = ""
    @Published var readingLevel = "early-reader"
    @Published var kidPhotoPath = ""
    @Published var anthropicApiKey = ""
    @Published var googleAiApiKey = ""
    @Published var serverURL: String {
        didSet {
            // Persist server URL whenever it changes
            UserDefaults.standard.set(serverURL, forKey: "serverURL")
            if let url = URL(string: serverURL) {
                APIClient.shared.baseURL = url
            }
        }
    }

    @Published var isLoading = false
    @Published var isSaving = false
    @Published var errorMessage: String?
    @Published var isServerOnline = false

    @Published var selectedPhoto: PhotosPickerItem?
    @Published var kidPhotoData: Data?

    static let readingLevels = [
        ("toddler", "Toddler (2-3)"),
        ("early-reader", "Early Reader (4-5)"),
        ("beginner", "Beginner (6-7)"),
        ("intermediate", "Intermediate (8-10)"),
    ]

    static let genderOptions = [
        ("", "Not specified"),
        ("boy", "Boy"),
        ("girl", "Girl"),
    ]

    init() {
        // Restore persisted server URL
        self.serverURL = UserDefaults.standard.string(forKey: "serverURL") ?? "http://localhost:3002"
    }

    func loadSettings() async {
        isLoading = true
        errorMessage = nil

        // Update API client base URL
        if let url = URL(string: serverURL) {
            APIClient.shared.baseURL = url
        }

        // Check server health
        isServerOnline = await APIClient.shared.checkHealth()

        guard isServerOnline else {
            isLoading = false
            errorMessage = "Cannot connect to server"
            return
        }

        do {
            let settings: SettingsResponse = try await APIClient.shared.get("/api/settings")
            kidName = settings.kidName
            UserDefaults.standard.set(kidName, forKey: "kidName")
            kidGender = settings.kidGender
            readingLevel = settings.readingLevel
            kidPhotoPath = settings.kidPhotoPath
            anthropicApiKey = settings.anthropicApiKey
            googleAiApiKey = settings.googleAiApiKey
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }

    func saveSettings() async {
        isSaving = true
        errorMessage = nil

        // Update API client base URL
        if let url = URL(string: serverURL) {
            APIClient.shared.baseURL = url
        }

        // Persist the server URL and kid name
        UserDefaults.standard.set(serverURL, forKey: "serverURL")
        UserDefaults.standard.set(kidName, forKey: "kidName")

        do {
            let request = SettingsUpdateRequest(
                kidName: kidName,
                kidGender: kidGender,
                readingLevel: readingLevel,
                anthropicApiKey: anthropicApiKey,
                googleAiApiKey: googleAiApiKey
            )
            let _: SettingsResponse = try await APIClient.shared.put("/api/settings", body: request)
        } catch {
            errorMessage = error.localizedDescription
        }

        isSaving = false
    }

    func uploadKidPhoto() async {
        guard let photoData = kidPhotoData else { return }

        do {
            let response = try await APIClient.shared.uploadPhoto(
                fileData: photoData,
                mimeType: "image/jpeg",
                type: "kid"
            )
            kidPhotoPath = response.photoPath
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func handlePhotoSelection() async {
        guard let item = selectedPhoto else { return }

        do {
            if let data = try await item.loadTransferable(type: Data.self) {
                kidPhotoData = data
                await uploadKidPhoto()
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
