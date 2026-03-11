import SwiftUI

/// In-memory image cache shared across all CachedImageView instances.
final class ImageCache: @unchecked Sendable {
    static let shared = ImageCache()
    private let cache = NSCache<NSURL, UIImage>()

    private init() {
        cache.countLimit = 100
    }

    func image(for url: URL) -> UIImage? {
        cache.object(forKey: url as NSURL)
    }

    func store(_ image: UIImage, for url: URL) {
        cache.setObject(image, forKey: url as NSURL)
    }
}

/// A reliable remote image view that uses URLSession directly.
///
/// `AsyncImage` is known to fail inside `UIHostingController` / `AnyView`
/// wrappers (e.g. when used within UIPageViewController's page-curl).
/// This view side-steps the issue by managing the network load itself.
struct CachedImageView: View {
    let url: URL

    @State private var uiImage: UIImage?
    @State private var hasFailed = false

    var body: some View {
        Group {
            if let uiImage {
                Image(uiImage: uiImage)
                    .resizable()
                    .scaledToFill()
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            } else if hasFailed {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color(.systemGray5))
                    .frame(maxWidth: .infinity, minHeight: 180)
                    .overlay {
                        Image(systemName: "photo")
                            .font(.largeTitle)
                            .foregroundStyle(.secondary)
                    }
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task(id: url) {
            await loadImage()
        }
    }

    private func loadImage() async {
        // Check cache first
        if let cached = ImageCache.shared.image(for: url) {
            uiImage = cached
            return
        }

        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            if let loaded = UIImage(data: data) {
                ImageCache.shared.store(loaded, for: url)
                uiImage = loaded
            } else {
                hasFailed = true
            }
        } catch {
            hasFailed = true
        }
    }
}
