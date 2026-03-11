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
            }
        }
        .padding()
        .background(.ultraThinMaterial)
    }

    private func cleanTitle(_ title: String) -> String {
        if let range = title.range(of: #"\s*[-:,]?\s*[Aa] bedtime story.*$"#, options: .regularExpression) {
            return String(title[title.startIndex..<range.lowerBound])
        }
        return title
    }
}
