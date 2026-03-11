import SwiftUI

struct HistoryListView: View {
    @StateObject private var viewModel = HistoryViewModel()

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading && viewModel.stories.isEmpty {
                    ProgressView()
                        .tint(Color.stPrimary)
                } else if viewModel.stories.isEmpty {
                    ContentUnavailableView(
                        "No Stories Yet",
                        systemImage: "book.closed",
                        description: Text("Your stories will appear here once you create one!")
                    )
                } else {
                    storyList
                }
            }
            .navigationTitle("History")
            .refreshable {
                await viewModel.loadHistory()
            }
            .task {
                await viewModel.loadHistory()
            }
        }
    }

    private var storyList: some View {
        List {
            ForEach(viewModel.stories) { story in
                NavigationLink {
                    HistoryDetailView(viewModel: viewModel, story: story)
                } label: {
                    storyRow(story)
                }
            }
            .onDelete { indexSet in
                Task {
                    for index in indexSet {
                        await viewModel.deleteStory(id: viewModel.stories[index].id)
                    }
                }
            }
        }
    }

    private func storyRow(_ story: StoryHistoryEntry) -> some View {
        HStack(spacing: 14) {
            // Story thumbnail placeholder
            RoundedRectangle(cornerRadius: 10)
                .fill(
                    LinearGradient(
                        colors: [Color.stPrimaryLight, Color.stPrimary.opacity(0.15)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: 56, height: 72)
                .overlay(
                    Image(systemName: "book.fill")
                        .font(.title3)
                        .foregroundStyle(Color.stPrimary.opacity(0.5))
                )

            VStack(alignment: .leading, spacing: 4) {
                Text(cleanTitle(story.title))
                    .font(.headline)
                    .foregroundStyle(Color.stTextPrimary)
                    .lineLimit(2)

                Text(story.prompt)
                    .font(.subheadline)
                    .foregroundStyle(Color.stTextSecondary)
                    .lineLimit(1)

                Text(relativeDate(story.createdAt))
                    .font(.caption)
                    .foregroundStyle(Color.stTextTertiary)
            }
        }
        .padding(.vertical, 4)
    }

    private func cleanTitle(_ title: String) -> String {
        // Remove subtitle like " - A bedtime story for ..."
        if let range = title.range(of: #"\s*[-:,]?\s*[Aa] bedtime story.*$"#, options: .regularExpression) {
            return String(title[title.startIndex..<range.lowerBound])
        }
        return title
    }

    private func relativeDate(_ isoString: String) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let date = formatter.date(from: isoString) ?? ISO8601DateFormatter().date(from: isoString) else {
            return isoString
        }

        let calendar = Calendar.current
        if calendar.isDateInToday(date) {
            let timeFormatter = DateFormatter()
            timeFormatter.timeStyle = .short
            return "Today, \(timeFormatter.string(from: date))"
        } else if calendar.isDateInYesterday(date) {
            return "Yesterday"
        } else {
            let days = calendar.dateComponents([.day], from: date, to: Date()).day ?? 0
            if days < 7 {
                return "\(days) days ago"
            } else {
                let displayFormatter = DateFormatter()
                displayFormatter.dateStyle = .medium
                return displayFormatter.string(from: date)
            }
        }
    }
}
