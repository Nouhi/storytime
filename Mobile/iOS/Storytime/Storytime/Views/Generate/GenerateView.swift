import SwiftUI

// MARK: - FlowLayout

/// A layout that arranges its children in a horizontal flow, wrapping to the next line when needed.
private struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let result = arrange(proposal: proposal, subviews: subviews)
        return result.size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = arrange(proposal: proposal, subviews: subviews)
        for (index, position) in result.positions.enumerated() {
            subviews[index].place(
                at: CGPoint(x: bounds.minX + position.x, y: bounds.minY + position.y),
                proposal: .unspecified
            )
        }
    }

    private struct ArrangeResult {
        var size: CGSize
        var positions: [CGPoint]
    }

    private func arrange(proposal: ProposedViewSize, subviews: Subviews) -> ArrangeResult {
        let maxWidth = proposal.width ?? .infinity
        var positions: [CGPoint] = []
        var currentX: CGFloat = 0
        var currentY: CGFloat = 0
        var lineHeight: CGFloat = 0
        var totalWidth: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if currentX + size.width > maxWidth && currentX > 0 {
                currentX = 0
                currentY += lineHeight + spacing
                lineHeight = 0
            }
            positions.append(CGPoint(x: currentX, y: currentY))
            lineHeight = max(lineHeight, size.height)
            currentX += size.width + spacing
            totalWidth = max(totalWidth, currentX - spacing)
        }

        return ArrangeResult(
            size: CGSize(width: totalWidth, height: currentY + lineHeight),
            positions: positions
        )
    }
}

// MARK: - GenerateView

struct GenerateView: View {
    @EnvironmentObject var localeManager: LocaleManager
    @StateObject private var viewModel = GenerationViewModel()
    @Environment(\.scenePhase) private var scenePhase
    @State private var showBedtimeMode = false

    var body: some View {
        NavigationStack {
            Group {
                switch viewModel.state {
                case .idle:
                    idleView
                case .generating:
                    generatingView
                case .complete:
                    completeView
                case .error(let message):
                    errorView(message: message)
                }
            }
            .navigationTitle("Storytime")
            .task {
                viewModel.refreshKidName()
                await viewModel.loadStyles()
                await viewModel.loadFamilyMembers()
            }
            .fullScreenCover(isPresented: $showBedtimeMode) {
                BedtimeView(isPresented: $showBedtimeMode)
            }
            .onChange(of: viewModel.shouldAutoActivateBedtime) { _, shouldActivate in
                if shouldActivate {
                    showBedtimeMode = true
                    viewModel.shouldAutoActivateBedtime = false
                    viewModel.storiesCompletedInSession = 0
                }
            }
            .onChange(of: scenePhase) { _, phase in
                if phase == .background {
                    viewModel.storiesCompletedInSession = 0
                }
            }
        }
    }

    // MARK: - Idle View

    private var idleView: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Personalized greeting
                VStack(spacing: 4) {
                    Text(viewModel.greeting)
                        .font(.title.bold())
                        .foregroundStyle(Color.stTextPrimary)
                        .multilineTextAlignment(.center)
                }
                .padding(.top, 8)
                .padding(.horizontal)

                // Setup prompt when no kid name
                if viewModel.kidName.isEmpty {
                    HStack(spacing: 12) {
                        Image(systemName: "person.fill.questionmark")
                            .font(.title3)
                            .foregroundStyle(Color.stSecondary)

                        Text(localeManager.localized("setup_prompt"))
                            .font(.subheadline)
                            .foregroundStyle(Color.stTextSecondary)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.stSecondaryLight)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .padding(.horizontal)
                }

                // Prompt input
                VStack(alignment: .leading, spacing: 8) {
                    Text(localeManager.localized("prompt_header"))
                        .font(.headline)
                        .foregroundStyle(Color.stTextPrimary)

                    TextEditor(text: $viewModel.prompt)
                        .frame(minHeight: 100, maxHeight: 150)
                        .padding(8)
                        .scrollContentBackground(.hidden)
                        .background(Color.stSurfaceCard)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(Color.stPrimary.opacity(0.2), lineWidth: 1.5)
                        )
                }
                .padding(.horizontal)

                // Suggestion chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(viewModel.suggestions, id: \.self) { suggestion in
                            Button {
                                viewModel.prompt = suggestion
                            } label: {
                                Text(suggestion)
                                    .stChip(isSelected: false)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal)
                }

                // Style pickers
                VStack(spacing: 12) {
                    stylePicker(
                        title: localeManager.localized("section_writing_style"),
                        selection: $viewModel.writingStyle,
                        options: viewModel.writingStyles,
                        customText: $viewModel.customWritingStyle
                    )

                    stylePicker(
                        title: localeManager.localized("section_image_style"),
                        selection: $viewModel.imageStyle,
                        options: viewModel.imageStyles,
                        customText: $viewModel.customImageStyle
                    )

                    if !viewModel.lessons.isEmpty {
                        stylePicker(
                            title: localeManager.localized("section_lesson"),
                            selection: $viewModel.lesson,
                            options: viewModel.lessons,
                            customText: $viewModel.customLesson
                        )
                    }
                }
                .padding(.horizontal)

                // Bedtime story toggle
                VStack(alignment: .leading, spacing: 4) {
                    Toggle(isOn: $viewModel.isBedtimeStory) {
                        Label(localeManager.localized("bedtime_story_label"), systemImage: "moon.zzz")
                    }
                    .tint(Color.stBedtime)

                    if viewModel.isBedtimeStory {
                        Text(localeManager.localized("bedtime_story_hint"))
                            .font(.caption2)
                            .foregroundStyle(Color.stTextTertiary)
                    }
                }
                .padding(viewModel.isBedtimeStory ? 16 : 0)
                .padding(.horizontal, viewModel.isBedtimeStory ? 0 : 0)
                .background(viewModel.isBedtimeStory ? Color.stBedtime.opacity(0.06) : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .padding(.horizontal)

                // Character picker
                if !viewModel.familyMembers.isEmpty {
                    characterPicker
                        .padding(.horizontal)
                }

                // Generate button
                Button {
                    Task { await viewModel.generate() }
                } label: {
                    Label(localeManager.localized("button_create_story"), systemImage: "sparkles")
                        .stGradientButton(isDisabled: viewModel.prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                .disabled(viewModel.prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .padding(.horizontal)
            }
            .padding(.vertical)
        }
    }

    // MARK: - Style Picker

    private func stylePicker(title: String, selection: Binding<String>, options: [StyleItem], customText: Binding<String>) -> some View {
        StylePickerView(title: title, selection: selection, options: options, customText: customText)
    }
}

// MARK: - StylePickerView

private struct StylePickerView: View {
    @EnvironmentObject var localeManager: LocaleManager
    let title: String
    @Binding var selection: String
    let options: [StyleItem]
    @Binding var customText: String
    @State private var showSheet = false
    @State private var draftCustomText = ""

    private var selectedOption: StyleItem? {
        options.first(where: { $0.id == selection })
    }

    private var isCustom: Bool {
        selection == "custom"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .stSectionHeader()

            Button { showSheet = true } label: {
                HStack {
                    if isCustom {
                        Text("✏️")
                            .font(.body)
                        Text(customText.isEmpty ? localeManager.localized("custom_label") : customText)
                            .foregroundStyle(Color.stTextPrimary)
                            .lineLimit(1)
                    } else if let sel = selectedOption {
                        Text(sel.emoji)
                            .font(.body)
                        Text(sel.label)
                            .foregroundStyle(Color.stTextPrimary)
                    }
                    Spacer()
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.caption)
                        .foregroundStyle(Color.stTextTertiary)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 11)
                .background(Color.stSurfaceCard)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.stPrimary.opacity(0.12), lineWidth: 1)
                )
            }
            .buttonStyle(.plain)

            if isCustom && !customText.isEmpty {
                Text(customText)
                    .font(.caption2)
                    .foregroundStyle(Color.stTextTertiary)
                    .lineLimit(2)
            } else if let sel = selectedOption {
                Text(sel.description)
                    .font(.caption2)
                    .foregroundStyle(Color.stTextTertiary)
            }
        }
        .sheet(isPresented: $showSheet) {
            NavigationStack {
                List {
                    ForEach(options) { style in
                        Button {
                            selection = style.id
                            showSheet = false
                        } label: {
                            HStack(spacing: 12) {
                                Text(style.emoji)
                                    .font(.title2)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(style.label)
                                        .font(.body)
                                        .fontWeight(style.id == selection ? .semibold : .regular)
                                        .foregroundStyle(.primary)
                                    Text(style.description)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }

                                Spacer()

                                if style.id == selection && !isCustom {
                                    Image(systemName: "checkmark")
                                        .foregroundStyle(Color.stPrimary)
                                        .fontWeight(.semibold)
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }

                    Section {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 8) {
                                Text("✏️")
                                    .font(.title2)
                                Text(localeManager.localized("custom_label"))
                                    .font(.body)
                                    .fontWeight(isCustom ? .semibold : .regular)
                                Spacer()
                                if isCustom {
                                    Image(systemName: "checkmark")
                                        .foregroundStyle(Color.stPrimary)
                                        .fontWeight(.semibold)
                                }
                            }

                            TextField(localeManager.localized("custom_placeholder", title.lowercased()), text: $draftCustomText, axis: .vertical)
                                .lineLimit(2...4)
                                .textFieldStyle(.roundedBorder)

                            Button {
                                customText = draftCustomText
                                selection = "custom"
                                showSheet = false
                            } label: {
                                Text(localeManager.localized("use_custom"))
                                    .font(.subheadline.bold())
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 8)
                                    .background(draftCustomText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? Color.gray.opacity(0.4) : Color.stPrimary)
                                    .foregroundStyle(.white)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                            .disabled(draftCustomText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        }
                    } header: {
                        Text(localeManager.localized("or_write_your_own"))
                    }
                }
                .navigationTitle(title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button(localeManager.localized("done")) { showSheet = false }
                    }
                }
            }
            .presentationDetents([.medium, .large])
            .onAppear {
                draftCustomText = customText
            }
        }
    }
}

// MARK: - GenerateView Helpers

private extension GenerateView {

    // MARK: - Character Picker

    private var characterPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(localeManager.localized("section_characters"))
                        .stSectionHeader()
                    Text(
                        viewModel.selectedCharacterIds.count == viewModel.familyMembers.count
                            ? localeManager.localized("characters_all_included")
                            : localeManager.localized("characters_count", viewModel.selectedCharacterIds.count, viewModel.familyMembers.count)
                    )
                    .font(.caption2)
                    .foregroundStyle(Color.stTextTertiary)
                }

                Spacer()

                if viewModel.selectedCharacterIds.count == viewModel.familyMembers.count {
                    Button(localeManager.localized("button_clear")) { viewModel.clearCharacterSelection() }
                        .font(.caption)
                        .foregroundStyle(Color.stPrimary)
                } else {
                    Button(localeManager.localized("button_select_all")) { viewModel.selectAllCharacters() }
                        .font(.caption)
                        .foregroundStyle(Color.stPrimary)
                }
            }

            FlowLayout(spacing: 8) {
                ForEach(viewModel.familyMembers) { member in
                    let isSelected = viewModel.selectedCharacterIds.contains(member.id)
                    Button {
                        viewModel.toggleCharacter(member.id)
                    } label: {
                        HStack(spacing: 4) {
                            Text(FamilyMembersViewModel.roleEmoji(member.role))
                                .font(.caption)
                            Text(member.name)
                                .font(.caption)
                        }
                        .stChip(isSelected: isSelected)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - Generating View

    private var generatingView: some View {
        VStack(spacing: 24) {
            Spacer()

            stepIcon
                .font(.system(size: 48))
                .symbolEffect(.pulse)

            Text(viewModel.stepDetail)
                .font(.headline)
                .multilineTextAlignment(.center)

            ProgressView(value: viewModel.progress, total: 100)
                .progressViewStyle(.linear)
                .padding(.horizontal, 40)

            Text("\(Int(viewModel.progress))%")
                .font(.caption)
                .foregroundStyle(.secondary)

            Spacer()

            Button {
                viewModel.cancelGeneration()
            } label: {
                Text(localeManager.localized("button_cancel"))
                    .foregroundStyle(.secondary)
            }
            .padding(.bottom, 20)
        }
        .padding()
    }

    private var stepIcon: some View {
        Group {
            switch viewModel.currentStep {
            case "generating-story":
                Image(systemName: "pencil.and.scribble")
            case "generating-images":
                Image(systemName: "paintpalette")
            case "assembling-ebook":
                Image(systemName: "book")
            default:
                Image(systemName: "sparkles")
            }
        }
        .foregroundStyle(Color.stPrimary)
    }

    // MARK: - Complete View

    private var completeView: some View {
        VStack(spacing: 0) {
            // Page viewer with navigation arrows
            if !viewModel.storyPages.isEmpty {
                ZStack {
                    PageCurlView(
                        currentPage: $viewModel.currentPage,
                        totalPages: viewModel.storyPages.count
                    ) { pageNum in
                        if let page = viewModel.storyPages.first(where: { $0.page == pageNum }) {
                            storyPageView(page: page)
                        }
                    }

                    PageNavigationOverlay(
                        currentPage: $viewModel.currentPage,
                        totalPages: viewModel.storyPages.count
                    )
                }
                .onChange(of: viewModel.currentPage) {
                    viewModel.prefetchAdjacentImages(currentPage: viewModel.currentPage)
                }
            }

            // Bottom bar
            VStack(spacing: 12) {
                // Page indicator
                Text(localeManager.localized("page_indicator", viewModel.currentPage, viewModel.storyPages.count))
                    .font(.caption)
                    .foregroundStyle(.secondary)

                // Actions
                HStack(spacing: 12) {
                    Button {
                        Task { await viewModel.saveEPUB() }
                    } label: {
                        Label(localeManager.localized("button_books"), systemImage: "book")
                            .font(.subheadline.bold())
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(Color.stPrimary)
                            .foregroundStyle(.white)
                            .clipShape(Capsule())
                    }

                    Button {
                        Task { await viewModel.savePDF() }
                    } label: {
                        Label(localeManager.localized("button_pdf"), systemImage: "doc.richtext")
                            .font(.subheadline.bold())
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(Color.stSecondary)
                            .foregroundStyle(.white)
                            .clipShape(Capsule())
                    }

                    Button {
                        viewModel.reset()
                    } label: {
                        Label(localeManager.localized("button_new"), systemImage: "plus")
                            .font(.subheadline.bold())
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(Color.stSurfaceCard)
                            .foregroundStyle(Color.stTextPrimary)
                            .clipShape(Capsule())
                            .overlay(Capsule().stroke(Color.stPrimary.opacity(0.15), lineWidth: 1))
                    }

                    Button {
                        showBedtimeMode = true
                    } label: {
                        Label(localeManager.localized("button_sleep"), systemImage: "moon.fill")
                            .font(.subheadline.bold())
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(Color.stBedtime)
                            .foregroundStyle(.white)
                            .clipShape(Capsule())
                    }
                }
            }
            .padding()
            .background(.ultraThinMaterial)
        }
    }

    private func storyPageView(page: StoryPage) -> some View {
        GeometryReader { geo in
            ScrollView {
                VStack(spacing: 0) {
                    if page.page == 1 {
                        coverPageLayout(page: page, size: geo.size)
                    } else if page.page == 16 {
                        closingPageLayout(page: page, size: geo.size)
                    } else {
                        storyPageLayout(page: page, size: geo.size)
                    }
                }
                .frame(minHeight: geo.size.height)
            }
        }
    }

    // MARK: - Cover page (page 1)

    private func coverPageLayout(page: StoryPage, size: CGSize) -> some View {
        VStack(spacing: 0) {
            // Cover image fills the top
            if viewModel.hasImages, let imageURL = viewModel.pageImageURL(page: page.page) {
                storyImage(url: imageURL)
                    .frame(height: size.height * 0.55)
                    .clipped()
            }

            Spacer(minLength: 16)

            // Title + subtitle
            VStack(spacing: 8) {
                if let range = page.text.range(of: #"\s*[-:,]?\s*[Aa] bedtime story"#, options: .regularExpression) {
                    Text(String(page.text[page.text.startIndex..<range.lowerBound]))
                        .font(.custom("Georgia-Bold", size: 28))
                        .multilineTextAlignment(.center)
                        .lineSpacing(4)
                    Text(String(page.text[range.lowerBound...])
                        .trimmingCharacters(in: .whitespaces)
                        .trimmingCharacters(in: CharacterSet(charactersIn: "-:, ")))
                        .font(.custom("Georgia-Italic", size: 16))
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                } else {
                    Text(page.text)
                        .font(.custom("Georgia-Bold", size: 28))
                        .multilineTextAlignment(.center)
                        .lineSpacing(4)
                }
            }
            .padding(.horizontal, 24)

            Spacer(minLength: 16)
        }
    }

    // MARK: - Story pages (2–15)

    private func storyPageLayout(page: StoryPage, size: CGSize) -> some View {
        VStack(spacing: 12) {
            // Image takes upper portion
            if viewModel.hasImages, let imageURL = viewModel.pageImageURL(page: page.page) {
                storyImage(url: imageURL)
                    .frame(height: size.height * 0.5)
                    .clipped()
                    .padding(.top, 8)
            }

            // Story text fills remaining space
            Spacer(minLength: 8)

            Text(page.text)
                .font(.custom("Georgia", size: 18))
                .lineSpacing(6)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            Spacer(minLength: 8)
        }
    }

    // MARK: - Closing page (page 16)

    private func closingPageLayout(page: StoryPage, size: CGSize) -> some View {
        VStack(spacing: 0) {
            if viewModel.hasImages, let imageURL = viewModel.pageImageURL(page: page.page) {
                storyImage(url: imageURL)
                    .frame(height: size.height * 0.5)
                    .clipped()
            }

            Spacer(minLength: 20)

            Text(page.text)
                .font(.custom("Georgia-BoldItalic", size: 20))
                .lineSpacing(6)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 28)

            Spacer(minLength: 20)
        }
    }

    // MARK: - Shared image component

    private func storyImage(url: URL) -> some View {
        CachedImageView(url: url)
            .padding(.horizontal, 12)
    }

    // MARK: - Error View

    private func errorView(message: String) -> some View {
        VStack(spacing: 20) {
            Spacer()

            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 48))
                .foregroundStyle(.red)

            Text(localeManager.localized("error_title"))
                .font(.headline)

            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Button {
                Task { await viewModel.generate() }
            } label: {
                Label(localeManager.localized("button_try_again"), systemImage: "arrow.clockwise")
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Color.stPrimary)
                    .foregroundStyle(.white)
                    .clipShape(Capsule())
            }

            Button(localeManager.localized("button_start_over")) {
                viewModel.reset()
            }
            .foregroundStyle(.secondary)

            Spacer()
        }
    }
}
