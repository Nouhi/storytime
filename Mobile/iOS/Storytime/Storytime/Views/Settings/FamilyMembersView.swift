import SwiftUI
import PhotosUI

struct FamilyMembersView: View {
    @StateObject private var viewModel = FamilyMembersViewModel()

    var body: some View {
        List {
            if viewModel.members.isEmpty && !viewModel.isLoading {
                ContentUnavailableView(
                    "No Family Members",
                    systemImage: "person.3",
                    description: Text("Add family members so they can appear in your stories")
                )
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
            }

            ForEach(viewModel.members) { member in
                memberRow(member)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        viewModel.prepareEdit(member: member)
                    }
            }
            .onDelete { indexSet in
                Task {
                    for index in indexSet {
                        await viewModel.deleteMember(id: viewModel.members[index].id)
                    }
                }
            }
        }
        .navigationTitle("Family Members")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    viewModel.prepareAdd()
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .overlay {
            if viewModel.isLoading {
                ProgressView()
            }
        }
        .alert("Error", isPresented: .constant(viewModel.errorMessage != nil)) {
            Button("OK") { viewModel.errorMessage = nil }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .sheet(isPresented: $viewModel.isShowingAddSheet) {
            addMemberSheet
        }
        .sheet(isPresented: $viewModel.isShowingEditSheet) {
            editMemberSheet
        }
        .task {
            await viewModel.loadMembers()
        }
        .refreshable {
            await viewModel.loadMembers()
        }
    }

    // MARK: - Member Row

    private func memberRow(_ member: FamilyMemberResponse) -> some View {
        HStack(spacing: 12) {
            // Photo or emoji avatar
            if let photoPath = member.photoPath, !photoPath.isEmpty {
                let cleanPath = photoPath.replacingOccurrences(of: "uploads/photos/", with: "")
                AsyncImage(url: APIClient.shared.imageURL(for: "/api/photos/\(cleanPath)")) { image in
                    image
                        .resizable()
                        .scaledToFill()
                } placeholder: {
                    Text(FamilyMembersViewModel.roleEmoji(member.role))
                        .font(.title2)
                }
                .frame(width: 44, height: 44)
                .clipShape(Circle())
            } else {
                Text(FamilyMembersViewModel.roleEmoji(member.role))
                    .font(.title2)
                    .frame(width: 44, height: 44)
                    .background(Color(.systemGray5))
                    .clipShape(Circle())
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(member.name)
                    .font(.body.bold())

                Text(member.role.replacingOccurrences(of: "-", with: " ").capitalized)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 2)
                    .background(Color.purple.opacity(0.1))
                    .clipShape(Capsule())

                if let description = member.description, !description.isEmpty {
                    Text(description)
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                        .lineLimit(2)
                }
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
        .padding(.vertical, 4)
    }

    // MARK: - Add Member Sheet

    private var addMemberSheet: some View {
        NavigationStack {
            Form {
                Section("Details") {
                    TextField("Name", text: $viewModel.editName)
                        .textInputAutocapitalization(.words)

                    Picker("Role", selection: $viewModel.editRole) {
                        ForEach(FamilyMembersViewModel.roles, id: \.0) { value, label in
                            Text(label).tag(value)
                        }
                    }
                }

                Section("Description (Optional)") {
                    TextEditor(text: $viewModel.editDescription)
                        .frame(minHeight: 60)
                        .overlay(alignment: .topLeading) {
                            if viewModel.editDescription.isEmpty {
                                Text("Appearance, personality, backstory...")
                                    .foregroundStyle(.tertiary)
                                    .padding(.top, 8)
                                    .padding(.leading, 4)
                                    .allowsHitTesting(false)
                            }
                        }
                }
            }
            .navigationTitle("Add Member")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        viewModel.isShowingAddSheet = false
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        Task { await viewModel.addMember() }
                    }
                    .disabled(viewModel.editName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .presentationDetents([.medium])
    }

    // MARK: - Edit Member Sheet

    private var editMemberSheet: some View {
        NavigationStack {
            Form {
                Section("Details") {
                    TextField("Name", text: $viewModel.editName)
                        .textInputAutocapitalization(.words)

                    Picker("Role", selection: $viewModel.editRole) {
                        ForEach(FamilyMembersViewModel.roles, id: \.0) { value, label in
                            Text(label).tag(value)
                        }
                    }
                }

                Section("Description (Optional)") {
                    TextEditor(text: $viewModel.editDescription)
                        .frame(minHeight: 60)
                        .overlay(alignment: .topLeading) {
                            if viewModel.editDescription.isEmpty {
                                Text("Appearance, personality, backstory...")
                                    .foregroundStyle(.tertiary)
                                    .padding(.top, 8)
                                    .padding(.leading, 4)
                                    .allowsHitTesting(false)
                            }
                        }
                }

                if let member = viewModel.editingMember {
                    Section("Photo") {
                        HStack {
                            if let photoData = viewModel.selectedPhotoData,
                               let uiImage = UIImage(data: photoData) {
                                Image(uiImage: uiImage)
                                    .resizable()
                                    .scaledToFill()
                                    .frame(width: 60, height: 60)
                                    .clipShape(Circle())
                            } else if let photoPath = member.photoPath, !photoPath.isEmpty {
                                let cleanPath = photoPath.replacingOccurrences(of: "uploads/photos/", with: "")
                                AsyncImage(url: APIClient.shared.imageURL(for: "/api/photos/\(cleanPath)")) { image in
                                    image
                                        .resizable()
                                        .scaledToFill()
                                } placeholder: {
                                    Text(FamilyMembersViewModel.roleEmoji(member.role))
                                        .font(.title)
                                }
                                .frame(width: 60, height: 60)
                                .clipShape(Circle())
                            } else {
                                Text(FamilyMembersViewModel.roleEmoji(member.role))
                                    .font(.largeTitle)
                                    .frame(width: 60, height: 60)
                                    .background(Color(.systemGray5))
                                    .clipShape(Circle())
                            }

                            Spacer()

                            PhotosPicker(selection: $viewModel.selectedPhoto, matching: .images) {
                                Label("Choose Photo", systemImage: "photo")
                            }
                        }
                        .onChange(of: viewModel.selectedPhoto) {
                            Task {
                                await viewModel.handlePhotoSelection()
                                if viewModel.selectedPhotoData != nil {
                                    await viewModel.uploadPhoto(memberId: member.id)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Edit Member")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        viewModel.isShowingEditSheet = false
                        viewModel.editingMember = nil
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        Task { await viewModel.updateMember() }
                    }
                    .disabled(viewModel.editName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .presentationDetents([.medium])
    }
}
