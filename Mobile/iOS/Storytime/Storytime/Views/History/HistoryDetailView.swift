import SwiftUI

struct HistoryDetailView: View {
    @EnvironmentObject var localeManager: LocaleManager
    @ObservedObject var viewModel: HistoryViewModel
    @Environment(\.dismiss) private var dismiss
    let story: StoryHistoryEntry

    @State private var showDeleteConfirmation = false

    var body: some View {
        VStack(spacing: 0) {
            if viewModel.isLoadingDetail {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if viewModel.storyPages.isEmpty {
                ContentUnavailableView(
                    localeManager.localized("story_not_found"),
                    systemImage: "doc.questionmark",
                    description: Text(localeManager.localized("story_not_found"))
                )
            } else {
                // Page viewer with navigation arrows
                ZStack {
                    PageCurlView(
                        currentPage: $viewModel.currentPage,
                        totalPages: viewModel.storyPages.count
                    ) { pageNum in
                        if let page = viewModel.storyPages.first(where: { $0.page == pageNum }) {
                            pageView(page: page)
                        }
                    }

                    PageNavigationOverlay(
                        currentPage: $viewModel.currentPage,
                        totalPages: viewModel.storyPages.count
                    )
                }
                .onChange(of: viewModel.currentPage) {
                    viewModel.prefetchAdjacentImages(
                        storyId: story.id,
                        currentPage: viewModel.currentPage,
                        hasImages: story.geminiImageCount > 0
                    )
                }

                // Bottom bar
                bottomBar
            }
        }
        .navigationTitle(cleanTitle(story.title))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showDeleteConfirmation = true
                } label: {
                    Image(systemName: "trash")
                        .foregroundStyle(.red)
                }
            }
        }
        .alert(localeManager.localized("delete_story_title"), isPresented: $showDeleteConfirmation) {
            Button(localeManager.localized("button_delete"), role: .destructive) {
                Task {
                    await viewModel.deleteStory(id: story.id)
                    dismiss()
                }
            }
            Button(localeManager.localized("button_cancel"), role: .cancel) { }
        } message: {
            Text(localeManager.localized("delete_story_message"))
        }
        .task {
            await viewModel.loadStoryDetail(id: story.id)
        }
        .overlay {
            if viewModel.bookletState != .idle {
                bookletDownloadOverlay
            }
        }
    }

    private func pageView(page: StoryPage) -> some View {
        GeometryReader { geo in
            ScrollView {
                VStack(spacing: 0) {
                    if page.page == 1 {
                        coverLayout(page: page, size: geo.size)
                    } else if page.page == 16 {
                        closingLayout(page: page, size: geo.size)
                    } else {
                        bodyLayout(page: page, size: geo.size)
                    }
                }
                .frame(minHeight: geo.size.height)
            }
        }
    }

    // MARK: - Cover page

    private func coverLayout(page: StoryPage, size: CGSize) -> some View {
        VStack(spacing: 0) {
            if story.geminiImageCount > 0 {
                historyImage(storyId: story.id, page: page.page)
                    .frame(height: size.height * 0.55)
                    .clipped()
            }

            Spacer(minLength: 16)

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

    // MARK: - Body pages (2–15)

    private func bodyLayout(page: StoryPage, size: CGSize) -> some View {
        VStack(spacing: 12) {
            if story.geminiImageCount > 0 {
                historyImage(storyId: story.id, page: page.page)
                    .frame(height: size.height * 0.5)
                    .clipped()
                    .padding(.top, 8)
            }

            Spacer(minLength: 8)

            Text(page.text)
                .font(.custom("Georgia", size: 18))
                .lineSpacing(6)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            Spacer(minLength: 8)
        }
    }

    // MARK: - Closing page

    private func closingLayout(page: StoryPage, size: CGSize) -> some View {
        VStack(spacing: 0) {
            if story.geminiImageCount > 0 {
                historyImage(storyId: story.id, page: page.page)
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

    // MARK: - Image component

    private func historyImage(storyId: Int, page: Int) -> some View {
        CachedImageView(url: viewModel.pageImageURL(storyId: storyId, page: page))
            .padding(.horizontal, 12)
    }

    private var bottomBar: some View {
        VStack(spacing: 12) {
            Text(localeManager.localized("page_indicator", viewModel.currentPage, viewModel.storyPages.count))
                .font(.caption)
                .foregroundStyle(.secondary)

            HStack(spacing: 12) {
                Button {
                    Task { await viewModel.downloadEPUB(storyId: story.id, title: story.title) }
                } label: {
                    Label(localeManager.localized("button_books"), systemImage: "book")
                        .font(.subheadline.bold())
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color.purple)
                        .foregroundStyle(.white)
                        .clipShape(Capsule())
                }

                Button {
                    Task { await viewModel.downloadPDF(storyId: story.id, title: story.title) }
                } label: {
                    Label(localeManager.localized("button_pdf"), systemImage: "doc.richtext")
                        .font(.subheadline.bold())
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color.orange)
                        .foregroundStyle(.white)
                        .clipShape(Capsule())
                }

                Button {
                    Task { await viewModel.downloadBookletPDF(storyId: story.id, title: story.title) }
                } label: {
                    Label(localeManager.localized("button_booklet"), systemImage: "printer")
                        .font(.subheadline.bold())
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color.green)
                        .foregroundStyle(.white)
                        .clipShape(Capsule())
                }
            }
        }
        .padding()
        .background(.ultraThinMaterial)
    }

    private var bookletDownloadOverlay: some View {
        ZStack {
            Color.black.opacity(0.7)
                .ignoresSafeArea()

            VStack(spacing: 20) {
                switch viewModel.bookletState {
                case .downloading:
                    PrintingAnimationView()

                    Text("Preparing your booklet")
                        .font(.headline)

                    ProgressView()
                        .progressViewStyle(.linear)
                        .tint(.green)
                        .frame(width: 200)

                    Text("Your story is being formatted\nfor printing")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)

                case .done(let message, let isError):
                    Image(systemName: isError ? "exclamationmark.circle.fill" : "checkmark.circle.fill")
                        .font(.system(size: 56))
                        .foregroundStyle(isError ? .red : .green)
                        .symbolEffect(.bounce, value: message)

                    Text(message)
                        .font(.headline)
                        .multilineTextAlignment(.center)

                    Button {
                        viewModel.bookletState = .idle
                    } label: {
                        Text("OK")
                            .font(.subheadline.bold())
                            .padding(.horizontal, 32)
                            .padding(.vertical, 10)
                            .background(isError ? Color.red : Color.green)
                            .foregroundStyle(.white)
                            .clipShape(Capsule())
                    }

                case .idle:
                    EmptyView()
                }
            }
            .padding(32)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24))
            .padding(48)
        }
    }

    private func cleanTitle(_ title: String) -> String {
        if let range = title.range(of: #"\s*[-:,]?\s*[Aa] bedtime story.*$"#, options: .regularExpression) {
            return String(title[title.startIndex..<range.lowerBound])
        }
        return title
    }
}

// MARK: - Printing Animation

private struct PrintingAnimationView: View {
    @State private var pageOffset: CGFloat = -30
    @State private var pageOpacity: Double = 0
    @State private var printerScale: CGFloat = 1.0
    @State private var stackCount = 0

    // Staggered page positions for the "printed stack"
    private let maxStack = 3

    var body: some View {
        ZStack {
            // Printed page stack below printer
            ForEach(0..<stackCount, id: \.self) { i in
                RoundedRectangle(cornerRadius: 3)
                    .fill(Color.white)
                    .frame(width: 36 - CGFloat(i * 2), height: 44 - CGFloat(i * 2))
                    .shadow(color: .black.opacity(0.08), radius: 1, y: 1)
                    .offset(y: 38 + CGFloat(i * 3))
                    .transition(.asymmetric(
                        insertion: .move(edge: .top).combined(with: .opacity),
                        removal: .opacity
                    ))
            }

            // Animated page sliding out of printer
            RoundedRectangle(cornerRadius: 2)
                .fill(Color.white)
                .frame(width: 32, height: 40)
                .shadow(color: .black.opacity(0.1), radius: 2, y: 2)
                .overlay(
                    VStack(spacing: 3) {
                        ForEach(0..<4, id: \.self) { _ in
                            RoundedRectangle(cornerRadius: 0.5)
                                .fill(Color.gray.opacity(0.3))
                                .frame(height: 2)
                        }
                    }
                    .padding(6)
                )
                .offset(y: pageOffset)
                .opacity(pageOpacity)

            // Printer icon on top
            Image(systemName: "printer.fill")
                .font(.system(size: 52))
                .foregroundStyle(.green)
                .scaleEffect(printerScale)
        }
        .frame(width: 100, height: 120)
        .onAppear {
            startPrintCycle()
        }
    }

    private func startPrintCycle() {
        // Page slides down from printer
        withAnimation(.easeIn(duration: 0.3)) {
            pageOpacity = 1
        }
        withAnimation(.easeOut(duration: 1.2)) {
            pageOffset = 30
        }

        // Printer "clunks" as it prints
        withAnimation(.easeInOut(duration: 0.15).delay(0.1)) {
            printerScale = 1.03
        }
        withAnimation(.easeInOut(duration: 0.15).delay(0.25)) {
            printerScale = 0.98
        }
        withAnimation(.easeInOut(duration: 0.15).delay(0.4)) {
            printerScale = 1.0
        }

        // Page lands in stack, then reset and loop
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
            withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) {
                stackCount = min(stackCount + 1, maxStack)
            }

            // Reset page for next cycle
            pageOffset = -30
            pageOpacity = 0

            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                // Reset stack if full, then print again
                if stackCount >= maxStack {
                    withAnimation(.easeOut(duration: 0.3)) {
                        stackCount = 0
                    }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                        startPrintCycle()
                    }
                } else {
                    startPrintCycle()
                }
            }
        }
    }
}
