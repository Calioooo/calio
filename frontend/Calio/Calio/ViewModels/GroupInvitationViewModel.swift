import Foundation
import Combine

@MainActor final class GroupInvitationViewModel: ObservableObject {
    @Published private(set) var invitations: [GroupInvitationSummaryResponseDTO] = []
    @Published private(set) var issuedInvitation: GroupInvitationResponseDTO?
    @Published private(set) var preview: PreviewGroupInvitationResponseDTO?
    @Published private(set) var acceptanceResult: AcceptGroupInvitationResponseDTO?
    @Published private(set) var isSubmitting = false
    @Published private(set) var errorMessage: String?
    private let service: GroupInvitationService
    init(service: GroupInvitationService = GroupInvitationService()) { self.service = service }
    func load(groupSpaceId: Int64) async { do { invitations = try await service.list(groupSpaceId: groupSpaceId) } catch { errorMessage = "초대 목록을 불러오지 못했습니다." } }
    func issue(groupSpaceId: Int64) async { do { issuedInvitation = try await service.issue(groupSpaceId: groupSpaceId); await load(groupSpaceId: groupSpaceId) } catch { errorMessage = "초대를 만들지 못했습니다." } }
    func revoke(groupSpaceId: Int64, invitationId: Int64) async { do { try await service.revoke(groupSpaceId: groupSpaceId, invitationId: invitationId); invitations.removeAll { $0.invitationId == invitationId } } catch { errorMessage = "초대를 취소하지 못했습니다." } }
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
    func clearError() { errorMessage = nil }
}
