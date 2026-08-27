import SwiftUI

struct GroupSpaceListView: View {
    @StateObject private var viewModel = GroupSpaceListViewModel()
    @State private var isPresentingCreation = false
    @State private var groupName = ""
    @State private var nickname = ""

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading && viewModel.spaces.isEmpty {
                    ProgressView("그룹 공간을 불러오는 중")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if viewModel.didFailLoading && viewModel.spaces.isEmpty {
                    ContentUnavailableView {
                        Label("그룹 공간을 불러오지 못했어요", systemImage: "wifi.exclamationmark")
                    } description: {
                        Text("잠시 후 다시 시도해 주세요.")
                    } actions: {
                        Button("다시 시도") { Task { await viewModel.load() } }
                            .buttonStyle(.borderedProminent)
                            .tint(.calioBrand)
                    }
                } else if viewModel.spaces.isEmpty {
                    ContentUnavailableView {
                        Label("그룹 공간", systemImage: "person.2")
                    } description: {
                        Text("함께 사용할 캘린더 공간을 만들어 보세요.")
                    } actions: {
                        Button("그룹 만들기") { isPresentingCreation = true }
                            .buttonStyle(.borderedProminent)
                            .tint(.calioBrand)
                    }
                } else {
                    List {
                        Section("내 그룹 공간") {
                            ForEach(viewModel.spaces, id: \.groupSpaceId) { space in
                                NavigationLink {
                                    GroupSpaceDetailView(groupSpace: space) { result in
                                        switch result {
                                        case .updated(let updatedSpace): viewModel.replace(updatedSpace)
                                        case .removed(let id): viewModel.remove(groupSpaceId: id)
                                        }
                                    }
                                } label: {
                                    GroupSpaceRow(space: space)
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                    .refreshable { await viewModel.load() }
                }
            }
            .background(Color.calioBackground)
            .navigationTitle("그룹 공간")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { isPresentingCreation = true } label: { Image(systemName: "plus") }
                        .accessibilityLabel("그룹 공간 만들기")
                }
            }
            .task { await viewModel.load() }
            .sheet(isPresented: $isPresentingCreation, onDismiss: resetCreationFields) {
                NavigationStack {
                    Form {
                        Section("그룹 정보") {
                            TextField("그룹 이름", text: $groupName)
                            TextField("내 닉네임", text: $nickname)
                        }
                    }
                    .navigationTitle("그룹 만들기")
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) { Button("취소") { isPresentingCreation = false } }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("만들기") {
                                Task { if await viewModel.create(name: groupName.trimmed, nickname: nickname.trimmed) { isPresentingCreation = false } }
                            }
                            .disabled(groupName.trimmed.isEmpty || nickname.trimmed.isEmpty)
                        }
                    }
                }
            }
            .alert("그룹 공간을 처리하지 못했어요", isPresented: errorBinding) {
                Button("확인", role: .cancel) { viewModel.clearError() }
            } message: { Text(viewModel.errorMessage ?? "잠시 후 다시 시도해 주세요.") }
        }
    }

    private var errorBinding: Binding<Bool> {
        Binding(
            get: { viewModel.errorMessage != nil && !viewModel.didFailLoading },
            set: { if !$0 { viewModel.clearError() } }
        )
    }
    private func resetCreationFields() { groupName = ""; nickname = "" }
}

private struct GroupSpaceRow: View {
    let space: GroupSpaceResponseDTO
    var body: some View {
        HStack(spacing: 12) {
            Text(space.emoji ?? String(space.name.prefix(1)))
                .font(.headline).foregroundStyle(.calioPrimary)
                .frame(width: 40, height: 40).background(Color.calioSelection, in: Circle())
            VStack(alignment: .leading, spacing: 3) {
                Text(space.name).font(.body.weight(.semibold))
                Text("멤버 \(space.memberCount)명 · \(space.myMembership.nickname)")
                    .font(.footnote).foregroundStyle(.calioTextSecondary)
            }
        }
        .padding(.vertical, 4)
    }
}

extension String { var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) } }
