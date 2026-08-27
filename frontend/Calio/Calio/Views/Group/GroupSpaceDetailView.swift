import SwiftUI

struct GroupSpaceDetailView: View {
    enum Result { case updated(GroupSpaceResponseDTO), removed(Int64) }
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel: GroupSpaceDetailViewModel
    @State private var isEditing = false
    @State private var editedName = ""
    @State private var confirmation: Confirmation?
    let onFinished: (Result) -> Void

    init(groupSpace: GroupSpaceResponseDTO, onFinished: @escaping (Result) -> Void) {
        _viewModel = StateObject(wrappedValue: GroupSpaceDetailViewModel(groupSpace: groupSpace))
        self.onFinished = onFinished
    }

    var body: some View {
        List {
            Section {
                HStack(spacing: 14) {
                    Text(viewModel.groupSpace.emoji ?? String(viewModel.groupSpace.name.prefix(1)))
                        .font(.title3.weight(.semibold)).foregroundStyle(.calioPrimary)
                        .frame(width: 56, height: 56).background(Color.calioSelection, in: Circle())
                    VStack(alignment: .leading, spacing: 4) {
                        Text(viewModel.groupSpace.name).font(.title3.weight(.semibold))
                        Text("멤버 \(viewModel.groupSpace.memberCount)명").font(.subheadline).foregroundStyle(.calioTextSecondary)
                    }
                }.padding(.vertical, 6)
            }
            Section("멤버") {
                if viewModel.isLoading && viewModel.members.isEmpty { ProgressView().frame(maxWidth: .infinity) }
                else { ForEach(viewModel.members, id: \.memberId) { member in memberRow(member) } }
            }
            Section("그룹 설정") {
                if viewModel.groupSpace.myMembership.role == .owner {
                    Button("그룹 이름 수정") { editedName = viewModel.groupSpace.name; isEditing = true }
                    Button("그룹 삭제", role: .destructive) { confirmation = .delete }
                } else { Button("그룹 나가기", role: .destructive) { confirmation = .leave } }
            }
        }
        .listStyle(.insetGrouped).background(Color.calioBackground)
        .navigationTitle("그룹 공간").navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.loadMembers() }
        .sheet(isPresented: $isEditing) {
            NavigationStack {
                Form { TextField("그룹 이름", text: $editedName) }.navigationTitle("그룹 이름 수정")
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) { Button("취소") { isEditing = false } }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("완료") { Task { if await viewModel.update(name: editedName.trimmed) { onFinished(.updated(viewModel.groupSpace)); isEditing = false } } }
                                .disabled(editedName.trimmed.isEmpty)
                        }
                    }
            }
        }
        .confirmationDialog(confirmation?.title ?? "", isPresented: Binding(get: { confirmation != nil }, set: { if !$0 { confirmation = nil } })) { confirmationButtons } message: { Text(confirmation?.message ?? "") }
        .alert("그룹 공간을 처리하지 못했어요", isPresented: errorBinding) { Button("확인", role: .cancel) { viewModel.clearError() } } message: { Text(viewModel.errorMessage ?? "잠시 후 다시 시도해 주세요.") }
    }

    @ViewBuilder private func memberRow(_ member: GroupMemberResponseDTO) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text(member.nickname).font(.body.weight(.medium))
                Text(member.role == .owner ? "소유자" : "멤버").font(.footnote).foregroundStyle(.calioTextSecondary)
            }; Spacer()
            if viewModel.groupSpace.myMembership.role == .owner && member.role != .owner {
                Menu {
                    Button("소유권 이전") { confirmation = .transfer(member) }
                    Button("멤버 내보내기", role: .destructive) { confirmation = .remove(member) }
                } label: { Image(systemName: "ellipsis.circle") }
            }
        }
    }

    @ViewBuilder private var confirmationButtons: some View {
        switch confirmation {
        case .delete: Button("그룹 삭제", role: .destructive) { Task { if await viewModel.delete() { onFinished(.removed(viewModel.groupSpace.groupSpaceId)); dismiss() } } }
        case .leave: Button("그룹 나가기", role: .destructive) { Task { if await viewModel.leave() { onFinished(.removed(viewModel.groupSpace.groupSpaceId)); dismiss() } } }
        case .remove(let member): Button("멤버 내보내기", role: .destructive) { Task { await viewModel.remove(member: member) } }
        case .transfer(let member): Button("소유권 이전", role: .destructive) {
            Task {
                if await viewModel.transferOwnership(to: member) {
                    onFinished(.updated(viewModel.groupSpace))
                }
            }
        }
        case nil: EmptyView()
        }
    }
    private var errorBinding: Binding<Bool> { Binding(get: { viewModel.errorMessage != nil }, set: { if !$0 { viewModel.clearError() } }) }
    private enum Confirmation {
        case delete, leave, remove(GroupMemberResponseDTO), transfer(GroupMemberResponseDTO)
        var title: String { switch self { case .delete: "그룹을 삭제할까요?"; case .leave: "그룹에서 나갈까요?"; case .remove(let member): "\(member.nickname)님을 내보낼까요?"; case .transfer(let member): "\(member.nickname)님에게 소유권을 이전할까요?" } }
        var message: String { switch self { case .delete: "삭제한 그룹 공간은 복구할 수 없습니다."; case .leave: "나간 뒤에는 초대를 통해 다시 참여할 수 있습니다."; case .remove: "내보낸 멤버는 그룹에 접근할 수 없습니다."; case .transfer: "이후에는 멤버 권한으로 전환됩니다." } }
    }
}
