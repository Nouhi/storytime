import SwiftUI
import PhotosUI

struct SettingsView: View {
    @EnvironmentObject var localeManager: LocaleManager
    @StateObject private var viewModel = SettingsViewModel()
    @AppStorage("bedtimeSound") private var bedtimeSound = "whiteNoise"
    @AppStorage("sleepTimerStories") private var sleepTimerStories = 0
    @State private var showDeveloperOptions = false

    var body: some View {
        NavigationStack {
            Form {
                serverSection
                languageSection
                childInfoSection
                familySection
                bedtimeSection
                developerSection
            }
            .navigationTitle(localeManager.localized("tab_settings"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(localeManager.localized("button_save")) {
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
            .alert(localeManager.localized("alert_error"), isPresented: .constant(viewModel.errorMessage != nil)) {
                Button(localeManager.localized("button_ok")) { viewModel.errorMessage = nil }
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
            .task {
                await viewModel.loadSettings()
            }
        }
    }

    // MARK: - Sections

    private var languageSection: some View {
        Section {
            Picker(localeManager.localized("section_language"), selection: Binding(
                get: { localeManager.languageCode },
                set: { localeManager.languageCode = $0 }
            )) {
                ForEach(GenerationViewModel.fallbackLanguages, id: \.id) { lang in
                    Text("\(lang.emoji) \(lang.description)").tag(lang.id)
                }
            }
        }
    }

    private var serverSection: some View {
        Section {
            HStack(spacing: 12) {
                // Status badge
                HStack(spacing: 6) {
                    Circle()
                        .fill(viewModel.isServerOnline ? Color.stAccent : .red)
                        .frame(width: 8, height: 8)
                    Text(viewModel.isServerOnline ? localeManager.localized("settings_connected") : localeManager.localized("settings_not_connected"))
                        .font(.subheadline)
                        .foregroundStyle(viewModel.isServerOnline ? Color.stAccent : .red)
                }

                Spacer()

                Button(localeManager.localized("settings_test")) {
                    Task { await viewModel.loadSettings() }
                }
                .font(.subheadline)
                .foregroundStyle(Color.stPrimary)
            }

            TextField(localeManager.localized("settings_server_url"), text: $viewModel.serverURL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
                .font(.subheadline)
                .foregroundStyle(Color.stTextSecondary)
        } header: {
            Text(localeManager.localized("settings_server"))
        }
    }

    private var childInfoSection: some View {
        Section(localeManager.localized("settings_child_details")) {
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
                        Text(localeManager.localized("settings_change_photo"))
                            .font(.caption)
                            .foregroundStyle(Color.stPrimary)
                    }
                }
                Spacer()
            }
            .onChange(of: viewModel.selectedPhoto) {
                Task { await viewModel.handlePhotoSelection() }
            }

            TextField(localeManager.localized("settings_name"), text: $viewModel.kidName)

            Picker(localeManager.localized("settings_gender"), selection: $viewModel.kidGender) {
                Text(localeManager.localized("settings_gender_not_set")).tag("")
                Text(localeManager.localized("settings_gender_boy")).tag("boy")
                Text(localeManager.localized("settings_gender_girl")).tag("girl")
            }

            Picker(localeManager.localized("settings_reading_level"), selection: $viewModel.readingLevel) {
                Text(localeManager.localized("settings_reading_toddler")).tag("toddler")
                Text(localeManager.localized("settings_reading_early")).tag("early-reader")
                Text(localeManager.localized("settings_reading_beginner")).tag("beginner")
                Text(localeManager.localized("settings_reading_intermediate")).tag("intermediate")
            }
        }
    }

    private var familySection: some View {
        Section(localeManager.localized("settings_family")) {
            NavigationLink {
                FamilyMembersView()
            } label: {
                HStack {
                    Image(systemName: "person.3.fill")
                        .foregroundStyle(Color.stPrimary)
                    Text(localeManager.localized("settings_family_members"))
                }
            }
        }
    }

    private var bedtimeSection: some View {
        Section {
            Picker(localeManager.localized("settings_ambient_sound"), selection: $bedtimeSound) {
                ForEach(AmbientSound.allCases) { sound in
                    Label(localeManager.localized("sound_\(sound.rawValue)"), systemImage: sound.iconName)
                        .tag(sound.rawValue)
                }
            }

            Picker(localeManager.localized("settings_sleep_timer"), selection: $sleepTimerStories) {
                Text(localeManager.localized("settings_sleep_off")).tag(0)
                Text(localeManager.localized("settings_sleep_1")).tag(1)
                Text(localeManager.localized("settings_sleep_2")).tag(2)
                Text(localeManager.localized("settings_sleep_3")).tag(3)
            }
        } header: {
            Text(localeManager.localized("settings_bedtime"))
        } footer: {
            Text(localeManager.localized("settings_bedtime_footer"))
        }
    }

    private var developerSection: some View {
        Section {
            DisclosureGroup(localeManager.localized("settings_developer_options"), isExpanded: $showDeveloperOptions) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(localeManager.localized("settings_anthropic_key"))
                        .font(.caption)
                        .foregroundStyle(Color.stTextSecondary)
                    SecureField("sk-ant-...", text: $viewModel.anthropicApiKey)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text(localeManager.localized("settings_google_key"))
                        .font(.caption)
                        .foregroundStyle(Color.stTextSecondary)
                    SecureField("AIza...", text: $viewModel.googleAiApiKey)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
            }
        } footer: {
            if showDeveloperOptions {
                Text(localeManager.localized("settings_keys_footer"))
            }
        }
    }
}
