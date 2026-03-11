import Foundation

class APIClient {
    static let shared = APIClient()

    var baseURL: URL

    private init() {
        // Restore saved server URL from UserDefaults
        let savedURL = UserDefaults.standard.string(forKey: "serverURL") ?? "http://localhost:3002"
        self.baseURL = URL(string: savedURL) ?? URL(string: "http://localhost:3002")!
    }

    private let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        return URLSession(configuration: config)
    }()

    // MARK: - URL Builder

    /// Build a full URL from a path string. Uses string concatenation instead of
    /// `appendingPathComponent` because that method percent-encodes `?` and `&`,
    /// breaking any query parameters (e.g. `/pages?page=1` → `/pages%3Fpage=1`).
    private func buildURL(_ path: String) -> URL {
        var base = baseURL.absoluteString
        if base.hasSuffix("/") && path.hasPrefix("/") {
            base = String(base.dropLast())
        } else if !base.hasSuffix("/") && !path.hasPrefix("/") {
            base += "/"
        }
        return URL(string: base + path) ?? baseURL.appendingPathComponent(path)
    }

    // MARK: - Generic Requests

    func get<T: Decodable>(_ path: String) async throws -> T {
        let url = buildURL(path)
        let (data, response) = try await session.data(from: url)
        try validateResponse(response)
        return try JSONDecoder().decode(T.self, from: data)
    }

    func put<T: Decodable, B: Encodable>(_ path: String, body: B) async throws -> T {
        let url = buildURL(path)
        var request = URLRequest(url: url)
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)
        let (data, response) = try await session.data(for: request)
        try validateResponse(response)
        return try JSONDecoder().decode(T.self, from: data)
    }

    func post<T: Decodable, B: Encodable>(_ path: String, body: B) async throws -> T {
        let url = buildURL(path)
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)
        let (data, response) = try await session.data(for: request)
        try validateResponse(response, data: data)
        return try JSONDecoder().decode(T.self, from: data)
    }

    func patch<T: Decodable, B: Encodable>(_ path: String, body: B) async throws -> T {
        let url = buildURL(path)
        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)
        let (data, response) = try await session.data(for: request)
        try validateResponse(response)
        return try JSONDecoder().decode(T.self, from: data)
    }

    func delete(_ path: String) async throws {
        let url = buildURL(path)
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        let (_, response) = try await session.data(for: request)
        try validateResponse(response)
    }

    // MARK: - Photo Upload

    func uploadPhoto(fileData: Data, mimeType: String, type: String, memberId: String? = nil) async throws -> UploadResponse {
        let url = buildURL("/api/upload")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"

        let boundary = UUID().uuidString
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        var body = Data()

        // File field
        let ext = mimeType.contains("png") ? "png" : "jpg"
        body.appendMultipart(boundary: boundary, name: "file", filename: "photo.\(ext)", mimeType: mimeType, data: fileData)

        // Type field
        body.appendMultipart(boundary: boundary, name: "type", value: type)

        // MemberId field (if provided)
        if let memberId = memberId {
            body.appendMultipart(boundary: boundary, name: "memberId", value: memberId)
        }

        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body

        let (data, response) = try await session.data(for: request)
        try validateResponse(response)
        return try JSONDecoder().decode(UploadResponse.self, from: data)
    }

    // MARK: - Binary Downloads

    func downloadData(_ path: String) async throws -> Data {
        let url = buildURL(path)
        let (data, response) = try await session.data(from: url)
        try validateResponse(response)
        return data
    }

    // MARK: - Image URL

    func imageURL(for path: String) -> URL {
        buildURL(path)
    }

    // MARK: - Health Check

    func checkHealth() async -> Bool {
        do {
            let _: [String: String] = try await get("/api/health")
            return true
        } catch {
            return false
        }
    }

    // MARK: - Helpers

    private func validateResponse(_ response: URLResponse, data: Data? = nil) throws {
        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }
        guard (200...299).contains(httpResponse.statusCode) else {
            // Try to extract error message from JSON response body
            if let data = data,
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let errorMessage = json["error"] as? String {
                throw APIError.serverMessage(errorMessage)
            }
            throw APIError.httpError(statusCode: httpResponse.statusCode)
        }
    }
}

enum APIError: LocalizedError {
    case invalidResponse
    case httpError(statusCode: Int)
    case serverMessage(String)

    var errorDescription: String? {
        switch self {
        case .invalidResponse:
            return "Invalid server response"
        case .httpError(let code):
            return "Server error (HTTP \(code))"
        case .serverMessage(let message):
            return message
        }
    }
}

// MARK: - Multipart Helper

extension Data {
    mutating func appendMultipart(boundary: String, name: String, filename: String, mimeType: String, data: Data) {
        append("--\(boundary)\r\n".data(using: .utf8)!)
        append("Content-Disposition: form-data; name=\"\(name)\"; filename=\"\(filename)\"\r\n".data(using: .utf8)!)
        append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        append(data)
        append("\r\n".data(using: .utf8)!)
    }

    mutating func appendMultipart(boundary: String, name: String, value: String) {
        append("--\(boundary)\r\n".data(using: .utf8)!)
        append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".data(using: .utf8)!)
        append("\(value)\r\n".data(using: .utf8)!)
    }
}
