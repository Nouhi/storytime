import SwiftUI
import PhotosUI

struct FamilyMembersView: View {
    @EnvironmentObject var localeManager: LocaleManager
    @StateObject private var viewModel = FamilyMembersViewModel()

    var body: some View {
        List {
            if viewModel.members.isEmpty && !viewModel.isLoading {
                ContentUnavailableView(
                    localeManager.localized("family_empty_title"),
                    systemImage: "person.3",
                    description: Text(localeManager.localized("family_empty_description"))
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
        .navigationTitle(localeManager.localized("settings_family_members"))
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
        .alert(localeManager.localized("alert_error"), isPresented: .constant(viewModel.errorMessage != nil)) {
            Button(localeManager.localized("button_ok")) { viewModel.errorMessage = nil }
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

                Text(localeManager.localized("role_\(member.role.replacingOccurrences(of: "-", with: "_"))"))
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
                Section(localeManager.localized("family_section_details")) {
                    TextField(localeManager.localized("settings_name"), text: $viewModel.editName)
                        .textInputAutocapitalization(.words)

                    Picker("Role", selection: $viewModel.editRole) {
                        ForEach(FamilyMembersViewModel.roles, id: \.0) { value, _ in
                            Text(localeManager.localized("role_\(value.replacingOccurrences(of: "-", with: "_"))")).tag(value)
                        }
                    }
                }

                Section(localeManager.localized("family_section_description")) {
                    TextEditor(text: $viewModel.editDescription)
                        .frame(minHeight: 60)
                        .overlay(alignment: .topLeading) {
                            if viewModel.editDescription.isEmpty {
                                Text(localeManager.localized("family_description_placeholder"))
                                    .foregroundStyle(.tertiary)
                                    .padding(.top, 8)
                                    .padding(.leading, 4)
                                    .allowsHitTesting(false)
                            }
                        }
                }
            }
            .navigationTitle(localeManager.localized("family_add_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localeManager.localized("button_cancel")) {
                        viewModel.isShowingAddSheet = false
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localeManager.localized("family_add_button")) {
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
                Section(localeManager.localized("family_section_details")) {
                    TextField(localeManager.localized("settings_name"), text: $viewModel.editName)
                        .textInputAutocapitalization(.words)

                    Picker("Role", selection: $viewModel.editRole) {
                        ForEach(FamilyMembersViewModel.roles, id: \.0) { value, _ in
                            Text(localeManager.localized("role_\(value.replacingOccurrences(of: "-", with: "_"))")).tag(value)
                        }
                    }
                }

                Section(localeManager.localized("family_section_description")) {
                    TextEditor(text: $viewModel.editDescription)
                        .frame(minHeight: 60)
                        .overlay(alignment: .topLeading) {
                            if viewModel.editDescription.isEmpty {
                                Text(localeManager.localized("family_description_placeholder"))
                                    .foregroundStyle(.tertiary)
                                    .padding(.top, 8)
                                    .padding(.leading, 4)
                                    .allowsHitTesting(false)
                            }
                        }
                }

                if let member = viewModel.editingMember {
                    Section(localeManager.localized("family_section_photo")) {
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
                                Label(localeManager.localized("family_choose_photo"), systemImage: "photo")
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
            .navigationTitle(localeManager.localized("family_edit_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localeManager.localized("button_cancel")) {
                        viewModel.isShowingEditSheet = false
                        viewModel.editingMember = nil
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localeManager.localized("button_save")) {
                        Task { await viewModel.updateMember() }
                    }
                    .disabled(viewModel.editName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .presentationDetents([.medium])
    }
}
