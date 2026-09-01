import SwiftUI

struct GroupInvitationJoinView: View {
    let onDismiss: () -> Void
    let onJoined: () -> Void
    @StateObject private var viewModel = GroupInvitationViewModel()
    @State private var credentialType: GroupInvitationCredentialKind = .inviteCode
    @State private var credential = ""
    @State private var nickname = ""

    init(
        onDismiss: @escaping () -> Void,
        onJoined: @escaping () -> Void = {}
    ) {
        self.onDismiss = onDismiss
        self.onJoined = onJoined
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.28)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)

            NavigationStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 30) {
                        if let preview = activePreview {
                            previewConfirmation(preview)
                        } else {
                            header
                            credentialForm
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 28)
                }
                .background(Color.calioBackground)
                .navigationTitle(activePreview == nil ? "그룹 참여" : "초대 확인")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    if activePreview != nil {
                        ToolbarItem(placement: .topBarLeading) {
                            Button { viewModel.clearAcceptanceFlow() } label: { Image(systemName: "chevron.left") }
                                .accessibilityLabel("초대 정보 다시 입력")
                        }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { onDismiss() } label: { Image(systemName: "xmark") }
                            .accessibilityLabel("닫기")
                    }
                }
                .alert("초대를 처리하지 못했어요", isPresented: errorBinding) {
                    Button("확인", role: .cancel) { viewModel.clearError() }
                } message: { Text(viewModel.errorMessage ?? "잠시 후 다시 시도해 주세요.") }
                .sheet(item: acceptanceResultBinding, onDismiss: viewModel.clearAcceptanceFlow) { result in
                    GroupInvitationAcceptanceResultSheet(
                        result: result,
                        onDone: {
                            onJoined()
                            onDismiss()
                        }
                    )
                        .presentationDetents([.height(300)])
                        .presentationDragIndicator(.visible)
                }
                .onDisappear { viewModel.clearAcceptanceFlow() }
            }
            .frame(maxWidth: 400, minHeight: cardHeight, maxHeight: cardHeight)
            .background(Color.calioBackground, in: RoundedRectangle(cornerRadius: 28))
            .clipShape(RoundedRectangle(cornerRadius: 28))
            .shadow(color: .black.opacity(0.22), radius: 24, y: 12)
            .padding(20)
        }
    }

    private var activePreview: GroupInvitationPreview? {
        viewModel.preview
    }

    private var cardHeight: CGFloat {
        activePreview == nil ? 480 : 550
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 16) {
            Image(systemName: "person.2.badge.plus")
                .font(.title3.weight(.semibold))
                .foregroundStyle(.calioPrimary)
                .frame(width: 50, height: 50)
                .background(Color.calioSelection, in: Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text("초대로 그룹에 참여")
                    .font(.title3.weight(.semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .layoutPriority(1)
                Text("받은 초대 코드나 링크를 입력하세요.")
                    .font(.subheadline)
                    .foregroundStyle(.calioTextSecondary)
                    .lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(18)
        .background(Color.calioSurface, in: RoundedRectangle(cornerRadius: 18))
    }

    private var credentialForm: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("초대 정보").font(.headline)
            Picker("입력 방식", selection: $credentialType) {
                Text("초대 코드").tag(GroupInvitationCredentialKind.inviteCode)
                Text("초대 링크").tag(GroupInvitationCredentialKind.linkToken)
            }
            .pickerStyle(.segmented)
            .padding(3)
            .frame(height: 50)
            .background(Color.calioSelection.opacity(0.72), in: RoundedRectangle(cornerRadius: 14))
            TextField(credentialType == .inviteCode ? "초대 코드" : "초대 링크", text: $credential)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .textFieldStyle(.plain)
                .padding(.horizontal, 16)
                .frame(height: 54)
                .background(Color.calioSurface, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.calioDivider, lineWidth: 1))
            Button {
                Task { _ = await viewModel.preview(type: credentialType, credential: credential.trimmed) }
            } label: {
                Text("초대 확인")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
            }
            .buttonStyle(.borderedProminent)
            .tint(.calioBrand)
            .disabled(credential.trimmed.isEmpty || viewModel.isSubmitting)
        }
    }

    private func previewConfirmation(_ preview: GroupInvitationPreview) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("이 그룹에 참여할까요?")
                .font(.title3.weight(.semibold))
            HStack(spacing: 14) {
                Text(preview.emoji ?? String(preview.name.prefix(1)))
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.calioPrimary)
                    .frame(width: 52, height: 52)
                    .background(Color.calioSelection, in: Circle())
                VStack(alignment: .leading, spacing: 4) {
                    Text(preview.name).font(.body.weight(.semibold))
                    Text("멤버 \(preview.memberCount)명")
                        .font(.subheadline)
                        .foregroundStyle(.calioTextSecondary)
                }
                Spacer()
            }
            .padding(18)
            .background(Color.calioSurface, in: RoundedRectangle(cornerRadius: 18))

            if let memberPreviews = preview.memberPreviews, !memberPreviews.isEmpty {
                HStack(spacing: 10) {
                    HStack(spacing: -8) {
                        ForEach(Array(memberPreviews.prefix(3).enumerated()), id: \.offset) { _, member in
                            Text(String(member.nickname.prefix(1)))
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.calioPrimary)
                                .frame(width: 30, height: 30)
                                .background(Color.calioSelection, in: Circle())
                                .overlay(Circle().stroke(Color.calioSurface, lineWidth: 2))
                        }
                    }
                    Text(memberPreviewSummary(memberPreviews, totalCount: preview.memberCount))
                        .font(.footnote)
                        .foregroundStyle(.calioTextSecondary)
                        .lineLimit(1)
                }
                .padding(.horizontal, 4)
            }

            Text("그룹에서 사용할 이름").font(.headline)
            TextField("닉네임", text: $nickname)
                .textFieldStyle(.plain)
                .padding(.horizontal, 16)
                .frame(height: 54)
                .background(Color.calioSurface, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.calioDivider, lineWidth: 1))

            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "clock.fill")
                    .foregroundStyle(.calioBrand)
                Text("이 초대는 \(koreanExpiryText(preview.expiresAt))까지 사용할 수 있어요.")
                    .font(.footnote)
                    .foregroundStyle(.calioPrimary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(14)
            .background(Color.calioSelection, in: RoundedRectangle(cornerRadius: 14))

            Button {
                Task { _ = await viewModel.accept(type: credentialType, credential: credential.trimmed, nickname: nickname.trimmed) }
            } label: {
                Text(viewModel.isSubmitting ? "참여 중" : "\(preview.name)에 참여")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
            }
            .buttonStyle(.borderedProminent)
            .tint(.calioBrand)
            .disabled(nickname.trimmed.isEmpty || viewModel.isSubmitting)
        }
    }

    private func memberPreviewSummary(_ members: [GroupInvitationMemberPreview], totalCount: Int) -> String {
        let visibleNames = members.prefix(3).map(\.nickname).joined(separator: "님, ")
        let remainingCount = max(totalCount - min(members.count, 3), 0)
        return remainingCount > 0 ? "\(visibleNames)님 외 \(remainingCount)명" : "\(visibleNames)님"
    }

    private func koreanExpiryText(_ date: Date) -> String {
        Self.expiryDateFormatter.string(from: date)
    }

    private static let expiryDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = .autoupdatingCurrent
        formatter.setLocalizedDateFormatFromTemplate("yMMMEdjmm")
        return formatter
    }()

    private var errorBinding: Binding<Bool> {
        Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.clearError() } }
        )
    }

    private var acceptanceResultBinding: Binding<GroupInvitationAcceptanceResult?> {
        Binding(
            get: { viewModel.acceptanceResult },
            set: { if $0 == nil { viewModel.clearAcceptanceFlow() } }
        )
    }
}

private struct GroupInvitationAcceptanceResultSheet: View {
    let result: GroupInvitationAcceptanceResult
    let onDone: () -> Void
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: result.joinResult == .alreadyMember ? "person.crop.circle.badge.checkmark" : "checkmark.circle.fill")
                .font(.system(size: 40))
                .foregroundStyle(.calioBrand)
            Text(result.joinResult == .alreadyMember ? "이미 참여한 그룹이에요" : "그룹에 참여했어요")
                .font(.title3.weight(.semibold))
            Text("\(result.groupSpaceName)에서 함께 일정을 관리할 수 있습니다.")
                .font(.subheadline)
                .foregroundStyle(.calioTextSecondary)
                .multilineTextAlignment(.center)
            Button("완료", action: onDone)
                .buttonStyle(.borderedProminent)
                .tint(.calioBrand)
        }
        .padding(24)
    }
}
