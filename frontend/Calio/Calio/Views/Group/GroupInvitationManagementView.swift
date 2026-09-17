import SwiftUI
import UIKit

struct GroupInvitationManagementView: View {
    let groupSpace: GroupSpace

    @StateObject private var viewModel = GroupInvitationViewModel()
    @State private var pendingRevocation: GroupInvitationSummary?
    @State private var isPresentingIssuedInvitation = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                groupSummary

                Button {
                    Task {
                        await viewModel.issue(groupSpaceId: groupSpace.groupSpaceId)
                        isPresentingIssuedInvitation = viewModel.issuedInvitation != nil
                    }
                } label: {
                    Label("새 초대 만들기", systemImage: "person.badge.plus")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                }
                .buttonStyle(.borderedProminent)
                .tint(.calioBrand)
                .disabled(viewModel.isSubmitting)

                Text("발급한 초대")
                    .font(.headline)

                invitationList
            }
            .padding(20)
        }
        .background(Color.calioBackground.ignoresSafeArea())
        .navigationTitle("초대 관리")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await viewModel.load(groupSpaceId: groupSpace.groupSpaceId)
        }
        .sheet(isPresented: $isPresentingIssuedInvitation) {
            if let invitation = viewModel.issuedInvitation {
                GroupInvitationIssuedSheet(invitation: invitation)
                    .presentationDetents([.height(340)])
                    .presentationDragIndicator(.visible)
            }
        }
        .confirmationDialog(
            "이 초대를 취소할까요?",
            isPresented: Binding(
                get: { pendingRevocation != nil },
                set: { if !$0 { pendingRevocation = nil } }
            )
        ) {
            Button("초대 취소", role: .destructive) {
                guard let invitation = pendingRevocation else { return }

                Task {
                    await viewModel.revoke(
                        groupSpaceId: groupSpace.groupSpaceId,
                        invitationId: invitation.id
                    )
                    pendingRevocation = nil
                }
            }
        } message: {
            Text("취소한 초대 링크와 코드는 더 이상 사용할 수 없습니다.")
        }
        .alert(
            "초대를 처리하지 못했어요",
            isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.clearError() } }
            )
        ) {
            Button("확인", role: .cancel) {
                viewModel.clearError()
            }
        } message: {
            Text(viewModel.errorMessage ?? "잠시 후 다시 시도해 주세요.")
        }
    }

    private var groupSummary: some View {
        HStack(spacing: 14) {
            Text(groupSpace.emoji ?? String(groupSpace.name.prefix(1)))
                .font(.title3.weight(.semibold))
                .foregroundStyle(.calioPrimary)
                .frame(width: 52, height: 52)
                .background(Color.calioSelection)
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: 4) {
                Text(groupSpace.name)
                    .font(.title3.weight(.semibold))

                Text("멤버 \(groupSpace.memberCount)명과 함께 사용 중")
                    .font(.subheadline)
                    .foregroundStyle(.calioTextSecondary)
            }

            Spacer()
        }
        .padding(16)
        .background(Color.calioSurface)
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }

    @ViewBuilder
    private var invitationList: some View {
        if viewModel.isLoadingInvitations {
            ProgressView()
                .frame(maxWidth: .infinity)
                .padding(.vertical, 24)
        } else if viewModel.invitations.isEmpty {
            ContentUnavailableView(
                "발급한 초대가 없습니다",
                systemImage: "paperplane",
                description: Text("새 초대를 만들어 함께할 사람에게 보내세요.")
            )
            .padding(.vertical, 24)
        } else {
            ForEach(viewModel.invitations, id: \.id) { invitation in
                HStack {
                    Image(systemName: "link")
                        .foregroundStyle(.calioBrand)

                    VStack(alignment: .leading, spacing: 4) {
                        Text("공유 가능한 초대")
                            .font(.subheadline.weight(.semibold))

                        Text("만료 \(invitation.expiresAt.formatted(date: .abbreviated, time: .shortened))")
                            .font(.footnote)
                            .foregroundStyle(.calioTextSecondary)
                    }

                    Spacer()

                    Button("취소") {
                        pendingRevocation = invitation
                    }
                    .foregroundStyle(Color.calendarHoliday)
                }
                .padding(14)
                .background(Color.calioSurface)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }
        }
    }
}

private struct GroupInvitationIssuedSheet: View {
    let invitation: IssuedGroupInvitation

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 20) {
            VStack(spacing: 6) {
                Text("초대를 만들었어요")
                    .font(.title3.weight(.semibold))

                Text("코드나 링크를 공유해 팀원을 초대하세요.")
                    .font(.subheadline)
                    .foregroundStyle(.calioTextSecondary)
            }

            VStack(spacing: 12) {
                Text(invitation.code)
                    .font(.title3.monospaced().weight(.bold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.calioSelection)
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                HStack(spacing: 12) {
                    Button {
                        UIPasteboard.general.string = invitation.code
                    } label: {
                        Label("코드 복사", systemImage: "doc.on.doc")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)

                    ShareLink(item: invitation.url) {
                        Label("링크 공유", systemImage: "square.and.arrow.up")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
            }

            Text("\(invitation.expiresAt.formatted(date: .abbreviated, time: .shortened))까지 사용할 수 있습니다.")
                .font(.footnote)
                .foregroundStyle(Color("CalioTextSecondary"))

            Button("완료") {
                dismiss()
            }
            .buttonStyle(.borderedProminent)
            .tint(.calioBrand)
            .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 20)
    }
}
