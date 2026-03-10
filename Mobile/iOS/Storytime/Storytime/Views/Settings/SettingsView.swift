import SwiftUI
import PhotosUI

struct SettingsView: View {
    @StateObject private var viewModel = SettingsViewModel()
    @AppStorage("bedtimeSound") private var bedtimeSound = "whiteNoise"
    @AppStorage("sleepTimerStories") private var sleepTimerStories = 0

    var body: some View {
        NavigationStack {
            Form {
                serverSection
                childInfoSection
                familySection
                bedtimeSection
                apiKeysSection
            }
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Save") {
                        Task { await viewModel.saveSettings() }
                    }
                    .disabled(viewModel.isSaving)
                }
            }
            .overlay {
                if viewModel.isLoading {
                    ProgressView()
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
            HStack {
                TextField("Server URL", text: $viewModel.serverURL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)

                Circle()
                    .fill(viewModel.isServerOnline ? .green : .red)
                    .frame(width: 10, height: 10)
            }

            Button("Test Connection") {
                Task { await viewModel.loadSettings() }
            }
        } header: {
            Text("Server")
        } footer: {
            Text("Enter the URL of your Storytime backend server")
        }
    }

    private var childInfoSection: some View {
        Section("Child Details") {
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

            // Photo picker
            HStack {
                VStack(alignment: .leading) {
                    Text("Photo")
                    if !viewModel.kidPhotoPath.isEmpty {
                        Text("Photo uploaded")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                Spacer()

                if let photoData = viewModel.kidPhotoData,
                   let uiImage = UIImage(data: photoData) {
                    Image(uiImage: uiImage)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 50, height: 50)
                        .clipShape(Circle())
                } else if !viewModel.kidPhotoPath.isEmpty {
                    AsyncImage(url: APIClient.shared.imageURL(for: "/api/photos/\(viewModel.kidPhotoPath.replacingOccurrences(of: "uploads/photos/", with: ""))")) { image in
                        image
                            .resizable()
                            .scaledToFill()
                    } placeholder: {
                        Image(systemName: "person.circle.fill")
                            .font(.title)
                            .foregroundStyle(.secondary)
                    }
                    .frame(width: 50, height: 50)
                    .clipShape(Circle())
                }

                PhotosPicker(selection: $viewModel.selectedPhoto, matching: .images) {
                    Label("Choose", systemImage: "photo")
                        .labelStyle(.iconOnly)
                }
            }
            .onChange(of: viewModel.selectedPhoto) {
                Task { await viewModel.handlePhotoSelection() }
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
                        .foregroundStyle(.purple)
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

    private var apiKeysSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 4) {
                Text("Anthropic API Key")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                SecureField("sk-ant-...", text: $viewModel.anthropicApiKey)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("Google AI API Key")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                SecureField("AIza...", text: $viewModel.googleAiApiKey)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
        } header: {
            Text("API Keys")
        } footer: {
            Text("Keys are stored on the server. Masked keys (e.g. sk-a...xyz) indicate an existing key is set.")
        }
    }
}
