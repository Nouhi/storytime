import Foundation

class SSEClient {
    private let session: URLSession

    init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 600 // 10 minutes for long generations
        self.session = URLSession(configuration: config)
    }

    func connect(url: URL) -> AsyncStream<GenerationEvent> {
        AsyncStream { continuation in
            let task = Task {
                do {
                    var request = URLRequest(url: url)
                    request.setValue("text/event-stream", forHTTPHeaderField: "Accept")

                    let (bytes, response) = try await session.bytes(for: request)

                    guard let httpResponse = response as? HTTPURLResponse,
                          (200...299).contains(httpResponse.statusCode) else {
                        let errorEvent = GenerationEvent(
                            type: "error", step: nil, detail: nil, progress: nil,
                            epubUrl: nil, storyId: nil, storyPages: nil, hasImages: nil,
                            message: "Server returned an error. Please try again."
                        )
                        continuation.yield(errorEvent)
                        continuation.finish()
                        return
                    }

                    var receivedTerminal = false

                    // Use bytes.lines for proper UTF-8 decoding (byte-by-byte
                    // reading breaks multi-byte characters like Arabic/Chinese/etc.)
                    for try await line in bytes.lines {
                        guard line.hasPrefix("data: ") else { continue }
                        let jsonString = String(line.dropFirst(6))
                        if let data = jsonString.data(using: .utf8),
                           let event = try? JSONDecoder().decode(GenerationEvent.self, from: data) {
                            continuation.yield(event)

                            if event.type == "complete" || event.type == "error" {
                                receivedTerminal = true
                                continuation.finish()
                                return
                            }
                        }
                    }

                    // Stream ended without a complete/error event — server likely crashed
                    if !receivedTerminal && !Task.isCancelled {
                        let errorEvent = GenerationEvent(
                            type: "error", step: nil, detail: nil, progress: nil,
                            epubUrl: nil, storyId: nil, storyPages: nil, hasImages: nil,
                            message: "Lost connection to the server. Please try again."
                        )
                        continuation.yield(errorEvent)
                    }
                    continuation.finish()
                } catch {
                    if !Task.isCancelled {
                        // Yield an error event
                        let errorEvent = GenerationEvent(
                            type: "error",
                            step: nil,
                            detail: nil,
                            progress: nil,
                            epubUrl: nil,
                            storyId: nil,
                            storyPages: nil,
                            hasImages: nil,
                            message: error.localizedDescription
                        )
                        continuation.yield(errorEvent)
                    }
                    continuation.finish()
                }
            }

            continuation.onTermination = { _ in
                task.cancel()
            }
        }
    }
}
