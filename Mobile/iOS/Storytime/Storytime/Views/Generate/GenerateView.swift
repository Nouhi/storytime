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
                        .font(.title2.bold())
                        .multilineTextAlignment(.center)
                }
                .padding(.top, 8)
                .padding(.horizontal)

                // Setup prompt when no kid name
                if viewModel.kidName.isEmpty {
                    HStack(spacing: 12) {
                        Image(systemName: "person.fill.questionmark")
                            .font(.title3)
                            .foregroundStyle(.orange)

                        Text("Set up your child's name in Settings for personalized stories")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.orange.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal)
                }

                // Prompt input
                VStack(alignment: .leading, spacing: 8) {
                    Text("What story shall we create?")
                        .font(.headline)

                    TextEditor(text: $viewModel.prompt)
                        .frame(minHeight: 100, maxHeight: 150)
                        .padding(8)
                        .background(Color(.systemGray6))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color(.systemGray4), lineWidth: 1)
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
                                    .font(.caption)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(Color(.systemGray6))
                                    .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal)
                }

                // Style pickers
                VStack(spacing: 12) {
                    stylePicker(
                        title: "Writing Style",
                        selection: $viewModel.writingStyle,
                        options: viewModel.writingStyles
                    )

                    stylePicker(
                        title: "Image Style",
                        selection: $viewModel.imageStyle,
                        options: viewModel.imageStyles
                    )
                }
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
                    Label("Create Story", systemImage: "sparkles")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(viewModel.prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? Color.gray : Color.purple)
                        .foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                }
                .disabled(viewModel.prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .padding(.horizontal)
            }
            .padding(.vertical)
        }
    }

    // MARK: - Style Picker

    private func stylePicker(title: String, selection: Binding<String>, options: [StyleItem]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.subheadline)
                .foregroundStyle(.secondary)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(options) { style in
                        Button {
                            selection.wrappedValue = style.id
                        } label: {
                            VStack(spacing: 4) {
                                Text(style.emoji)
                                    .font(.title2)
                                Text(style.label)
                                    .font(.caption2)
                                    .lineLimit(1)
                            }
                            .frame(width: 70, height: 60)
                            .background(selection.wrappedValue == style.id ? Color.purple.opacity(0.15) : Color(.systemGray6))
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(selection.wrappedValue == style.id ? Color.purple : Color.clear, lineWidth: 2)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    // MARK: - Character Picker

    private var characterPicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Characters")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Text(
                        viewModel.selectedCharacterIds.count == viewModel.familyMembers.count
                            ? "All included"
                            : "\(viewModel.selectedCharacterIds.count) of \(viewModel.familyMembers.count) selected"
                    )
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
                }

                Spacer()

                if viewModel.selectedCharacterIds.count == viewModel.familyMembers.count {
                    Button("Clear") { viewModel.clearCharacterSelection() }
                        .font(.caption)
                } else {
                    Button("Select All") { viewModel.selectAllCharacters() }
                        .font(.caption)
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
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(isSelected ? Color.purple.opacity(0.15) : Color(.systemGray6))
                        .clipShape(Capsule())
                        .overlay(
                            Capsule()
                                .stroke(isSelected ? Color.purple : Color.clear, lineWidth: 1.5)
                        )
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
        .foregroundStyle(.purple)
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
                Text("Page \(viewModel.currentPage) of \(viewModel.storyPages.count)")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                // Actions
                HStack(spacing: 12) {
                    Button {
                        Task { await viewModel.saveEPUB() }
                    } label: {
                        Label("Books", systemImage: "book")
                            .font(.subheadline.bold())
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(Color.purple)
                            .foregroundStyle(.white)
                            .clipShape(Capsule())
                    }

                    Button {
                        Task { await viewModel.savePDF() }
                    } label: {
                        Label("PDF", systemImage: "doc.richtext")
                            .font(.subheadline.bold())
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(Color.orange)
                            .foregroundStyle(.white)
                            .clipShape(Capsule())
                    }

                    Button {
                        viewModel.reset()
                    } label: {
                        Label("New", systemImage: "plus")
                            .font(.subheadline.bold())
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(Color(.systemGray5))
                            .clipShape(Capsule())
                    }

                    Button {
                        showBedtimeMode = true
                    } label: {
                        Label("Sleep", systemImage: "moon.fill")
                            .font(.subheadline.bold())
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(Color.indigo)
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

            Text("Something went wrong")
                .font(.headline)

            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Button {
                Task { await viewModel.generate() }
            } label: {
                Label("Try Again", systemImage: "arrow.clockwise")
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Color.purple)
                    .foregroundStyle(.white)
                    .clipShape(Capsule())
            }

            Button("Start Over") {
                viewModel.reset()
            }
            .foregroundStyle(.secondary)

            Spacer()
        }
    }
}
