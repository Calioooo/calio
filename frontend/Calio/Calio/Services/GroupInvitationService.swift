import Foundation

struct GroupInvitationService {
    private let repository: GroupSpaceRepository
    init(repository: GroupSpaceRepository = URLSessionGroupSpaceRepository()) { self.repository = repository }
    func issue(groupSpaceId: Int64) async throws -> GroupInvitationResponseDTO { try await repository.issueInvitation(groupSpaceId: groupSpaceId) }
    func list(groupSpaceId: Int64) async throws -> [GroupInvitationSummaryResponseDTO] { try await repository.fetchInvitations(groupSpaceId: groupSpaceId).invitations }
    func revoke(groupSpaceId: Int64, invitationId: Int64) async throws { try await repository.revokeInvitation(groupSpaceId: groupSpaceId, invitationId: invitationId) }
    func preview(type: GroupInvitationCredentialKind, credential: String) async throws -> PreviewGroupInvitationResponseDTO { try await repository.previewInvitation(.init(credentialType: type.previewType, credential: credential)) }
    func accept(type: GroupInvitationCredentialKind, credential: String, nickname: String) async throws -> AcceptGroupInvitationResponseDTO { try await repository.acceptInvitation(.init(credentialType: type.acceptanceType, credential: credential, nickname: nickname)) }
}
