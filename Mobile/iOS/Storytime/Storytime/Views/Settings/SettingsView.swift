import SwiftUI
import PhotosUI

struct SettingsView: View {
    @StateObject private var viewModel = SettingsViewModel()
    @AppStorage("bedtimeSound") private var bedtimeSound = "whiteNoise"
    @AppStorage("sleepTimerStories") private var sleepTimerStories = 0
    @State private var showDeveloperOptions = false

    var body: some View {
        NavigationStack {
            Form {
                serverSection
                childInfoSection
                familySection
                bedtimeSection
                developerSection
            }
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Save") {
                        Task { await viewModel.saveSettings() }
                    }
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.stPrimary)
                    .disabled(viewModel.isSaving)
                }
            }
            .overlay {
                if viewModel.isLoading {
                    ProgressView()
                        .tint(Color.stPrimary)
                }
            }
            .alert("Error", isPresented: .constant(viewModel.errorMessage != nil)) {
                Button("OK") { viewModel.errorMessage = nil }
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
            .task {
                await viewModel.loadSettings()
            }
        }
    }

    // MARK: - Sections

    private var serverSection: some View {
        Section {
            HStack(spacing: 12) {
                // Status badge
                HStack(spacing: 6) {
                    Circle()
                        .fill(viewModel.isServerOnline ? Color.stAccent : .red)
                        .frame(width: 8, height: 8)
                    Text(viewModel.isServerOnline ? "Connected" : "Not connected")
                        .font(.subheadline)
                        .foregroundStyle(viewModel.isServerOnline ? Color.stAccent : .red)
                }

                Spacer()

                Button("Test") {
                    Task { await viewModel.loadSettings() }
                }
                .font(.subheadline)
                .foregroundStyle(Color.stPrimary)
            }

            TextField("Server URL", text: $viewModel.serverURL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
                .font(.subheadline)
                .foregroundStyle(Color.stTextSecondary)
        } header: {
            Text("Server")
        }
    }

    private var childInfoSection: some View {
        Section("Child Details") {
            // Photo — prominent and centered
            HStack {
                Spacer()
                VStack(spacing: 8) {
                    if let photoData = viewModel.kidPhotoData,
                       let uiImage = UIImage(data: photoData) {
                        Image(uiImage: uiImage)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 80, height: 80)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Color.stPrimary.opacity(0.2), lineWidth: 2))
                    } else if !viewModel.kidPhotoPath.isEmpty {
                        AsyncImage(url: APIClient.shared.imageURL(for: "/api/photos/\(viewModel.kidPhotoPath.replacingOccurrences(of: "uploads/photos/", with: ""))")) { image in
                            image
                                .resizable()
                                .scaledToFill()
                        } placeholder: {
                            Image(systemName: "person.circle.fill")
                                .font(.system(size: 40))
                                .foregroundStyle(Color.stTextTertiary)
                        }
                        .frame(width: 80, height: 80)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(Color.stPrimary.opacity(0.2), lineWidth: 2))
                    } else {
                        Circle()
                            .fill(Color.stPrimaryLight)
                            .frame(width: 80, height: 80)
                            .overlay(
                                Image(systemName: "person.fill")
                                    .font(.title)
                                    .foregroundStyle(Color.stPrimary.opacity(0.4))
                            )
                    }

                    PhotosPicker(selection: $viewModel.selectedPhoto, matching: .images) {
                        Text("Change Photo")
                            .font(.caption)
                            .foregroundStyle(Color.stPrimary)
                    }
                }
                Spacer()
            }
            .onChange(of: viewModel.selectedPhoto) {
                Task { await viewModel.handlePhotoSelection() }
            }

            TextField("Name", text: $viewModel.kidName)

            Picker("Gender", selection: $viewModel.kidGender) {
                ForEach(SettingsViewModel.genderOptions, id: \.0) { value, label in
                    Text(label).tag(value)
                }
            }

            Picker("Reading Level", selection: $viewModel.readingLevel) {
                ForEach(SettingsViewModel.readingLevels, id: \.0) { value, label in
                    Text(label).tag(value)
                }
            }
        }
    }

    private var familySection: some View {
        Section("Family") {
            NavigationLink {
                FamilyMembersView()
            } label: {
                HStack {
                    Image(systemName: "person.3.fill")
                        .foregroundStyle(Color.stPrimary)
                    Text("Family Members")
                }
            }
        }
    }

    private var bedtimeSection: some View {
        Section {
            Picker("Ambient Sound", selection: $bedtimeSound) {
                ForEach(AmbientSound.allCases) { sound in
                    Label(sound.displayName, systemImage: sound.iconName)
                        .tag(sound.rawValue)
                }
            }

            Picker("Sleep Timer", selection: $sleepTimerStories) {
                Text("Off").tag(0)
                Text("After 1 story").tag(1)
                Text("After 2 stories").tag(2)
                Text("After 3 stories").tag(3)
            }
        } header: {
            Text("Bedtime")
        } footer: {
            Text("Bedtime mode dims the screen and plays soothing sounds after story time")
        }
    }

    private var developerSection: some View {
        Section {
            DisclosureGroup("Developer Options", isExpanded: $showDeveloperOptions) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Anthropic API Key")
                        .font(.caption)
                        .foregroundStyle(Color.stTextSecondary)
                    SecureField("sk-ant-...", text: $viewModel.anthropicApiKey)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("Google AI API Key")
                        .font(.caption)
                        .foregroundStyle(Color.stTextSecondary)
                    SecureField("AIza...", text: $viewModel.googleAiApiKey)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
            }
        } footer: {
            if showDeveloperOptions {
                Text("Keys are stored on the server. Masked keys indicate an existing key is set.")
            }
        }
    }
}
