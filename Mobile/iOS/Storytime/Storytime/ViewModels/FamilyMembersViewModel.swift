import Foundation
import SwiftUI
import PhotosUI

@MainActor
class FamilyMembersViewModel: ObservableObject {
    @Published var members: [FamilyMemberResponse] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    // Add / Edit sheet state
    @Published var isShowingAddSheet = false
    @Published var isShowingEditSheet = false
    @Published var editingMember: FamilyMemberResponse?

    @Published var editName = ""
    @Published var editRole = "brother"
    @Published var editDescription = ""

    // Photo
    @Published var selectedPhoto: PhotosPickerItem?
    @Published var selectedPhotoData: Data?

    static let roles = [
        ("mom", "Mom"),
        ("dad", "Dad"),
        ("brother", "Brother"),
        ("sister", "Sister"),
        ("grandma", "Grandma"),
        ("grandpa", "Grandpa"),
        ("aunt", "Aunt"),
        ("uncle", "Uncle"),
        ("pet", "Pet"),
        ("friend", "Friend"),
        ("companion", "Companion"),
        ("classmate", "Classmate"),
        ("neighbor", "Neighbor"),
        ("magical-friend", "Magical Friend"),
        ("other", "Other"),
    ]

    static func roleEmoji(_ role: String) -> String {
        switch role {
        case "mom": return "👩"
        case "dad": return "👨"
        case "brother": return "👦"
        case "sister": return "👧"
        case "grandma": return "👵"
        case "grandpa": return "👴"
        case "aunt": return "👩"
        case "uncle": return "👨"
        case "pet": return "🐾"
        case "friend": return "👫"
        case "companion": return "🤝"
        case "classmate": return "🎒"
        case "neighbor": return "🏠"
        case "magical-friend": return "✨"
        default: return "👤"
        }
    }

    func loadMembers() async {
        isLoading = true
        errorMessage = nil

        do {
            members = try await APIClient.shared.get("/api/family-members")
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }

    func addMember() async {
        guard !editName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }

        do {
            let trimmedDesc = editDescription.trimmingCharacters(in: .whitespacesAndNewlines)
            let request = FamilyMemberCreateRequest(
                name: editName.trimmingCharacters(in: .whitespacesAndNewlines),
                role: editRole,
                description: trimmedDesc.isEmpty ? nil : trimmedDesc
            )
            let _: FamilyMemberResponse = try await APIClient.shared.post("/api/family-members", body: request)
            await loadMembers()
            isShowingAddSheet = false
            resetEditFields()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func updateMember() async {
        guard let member = editingMember else { return }
        guard !editName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }

        do {
            let trimmedDesc = editDescription.trimmingCharacters(in: .whitespacesAndNewlines)
            let request = FamilyMemberUpdateRequest(
                name: editName.trimmingCharacters(in: .whitespacesAndNewlines),
                role: editRole,
                description: trimmedDesc.isEmpty ? nil : trimmedDesc
            )
            let _: FamilyMemberResponse = try await APIClient.shared.patch("/api/family-members/\(member.id)", body: request)
            await loadMembers()
            isShowingEditSheet = false
            editingMember = nil
            resetEditFields()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func deleteMember(id: Int) async {
        do {
            try await APIClient.shared.delete("/api/family-members/\(id)")
            members.removeAll { $0.id == id }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func uploadPhoto(memberId: Int) async {
        guard let photoData = selectedPhotoData else { return }

        do {
            let _ = try await APIClient.shared.uploadPhoto(
                fileData: photoData,
                mimeType: "image/jpeg",
                type: "member",
                memberId: String(memberId)
            )
            await loadMembers()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func handlePhotoSelection() async {
        guard let item = selectedPhoto else { return }

        do {
            if let data = try await item.loadTransferable(type: Data.self) {
                selectedPhotoData = data
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func prepareAdd() {
        resetEditFields()
        isShowingAddSheet = true
    }

    func prepareEdit(member: FamilyMemberResponse) {
        editingMember = member
        editName = member.name
        editRole = member.role
        editDescription = member.description ?? ""
        isShowingEditSheet = true
    }

    private func resetEditFields() {
        editName = ""
        editRole = "brother"
        editDescription = ""
        selectedPhoto = nil
        selectedPhotoData = nil
    }
}
