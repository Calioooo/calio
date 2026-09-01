import Foundation
import Combine

@MainActor final class GroupInvitationViewModel: ObservableObject {
    @Published private(set) var invitations: [GroupInvitationSummary] = []
    @Published private(set) var issuedInvitation: IssuedGroupInvitation?
    @Published private(set) var preview: GroupInvitationPreview?
    @Published private(set) var acceptanceResult: GroupInvitationAcceptanceResult?
    @Published private(set) var isSubmitting = false
    @Published private(set) var errorMessage: String?
    private let service: GroupInvitationService
    private var latestLoadRequestID = 0
    init(service: GroupInvitationService = GroupInvitationService()) {
        self.service = service
    }

    func load(groupSpaceId: Int64) async {
        latestLoadRequestID += 1
        let requestID = latestLoadRequestID

        do {
            let loadedInvitations = try await service.list(groupSpaceId: groupSpaceId)
            guard requestID == latestLoadRequestID else { return }

            invitations = loadedInvitations
            errorMessage = nil
        } catch {
            guard requestID == latestLoadRequestID else { return }

            errorMessage = "초대 목록을 불러오지 못했습니다."
        }
    }
    func issue(groupSpaceId: Int64) async {
        guard !isSubmitting else { return }
        invalidateInvitationLoads()
        isSubmitting = true
        defer { isSubmitting = false }
        issuedInvitation = nil
        do {
            issuedInvitation = try await service.issue(groupSpaceId: groupSpaceId)
            await load(groupSpaceId: groupSpaceId)
        } catch {
            errorMessage = "초대를 만들지 못했습니다."
        }
    }
    func revoke(groupSpaceId: Int64, invitationId: Int64) async {
        invalidateInvitationLoads()

        do {
            try await service.revoke(groupSpaceId: groupSpaceId, invitationId: invitationId)
            invitations.removeAll { $0.id == invitationId }
        } catch {
            errorMessage = "초대를 취소하지 못했습니다."
        }
    }
    func preview(type: GroupInvitationCredentialKind, credential: String) async -> Bool {
        isSubmitting = true
        defer { isSubmitting = false }
        do {
            preview = try await service.preview(type: type, credential: credential)
            errorMessage = nil
            return true
        } catch {
            errorMessage = "초대를 확인하지 못했습니다. 코드나 링크를 다시 확인해 주세요."
            return false
        }
    }
    func accept(type: GroupInvitationCredentialKind, credential: String, nickname: String) async -> Bool {
        isSubmitting = true
        defer { isSubmitting = false }
        do {
            acceptanceResult = try await service.accept(type: type, credential: credential, nickname: nickname)
            errorMessage = nil
            return true
        } catch {
            errorMessage = "그룹 공간에 참여하지 못했습니다. 다시 시도해 주세요."
            return false
        }
    }
    func clearAcceptanceFlow() {
        preview = nil
        acceptanceResult = nil
        errorMessage = nil
    }
    func clearError() {
        errorMessage = nil
    }

    private func invalidateInvitationLoads() {
        latestLoadRequestID += 1
    }
}
