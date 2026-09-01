import Foundation

struct GroupInvitationService {
    private let repository: GroupSpaceRepository
    init(repository: GroupSpaceRepository = URLSessionGroupSpaceRepository()) {
        self.repository = repository
    }
    func issue(groupSpaceId: Int64) async throws -> IssuedGroupInvitation {
        let response = try await performGroupSpaceRequest { try await repository.issueInvitation(groupSpaceId: groupSpaceId) }
        return .init(id: response.invitationId, url: response.inviteUrl, code: response.inviteCode, expiresAt: response.expiresAt)
    }
    func list(groupSpaceId: Int64) async throws -> [GroupInvitationSummary] {
        try await performGroupSpaceRequest { try await repository.fetchInvitations(groupSpaceId: groupSpaceId) }.invitations.map {
            .init(id: $0.invitationId, expiresAt: $0.expiresAt)
        }
    }
    func revoke(groupSpaceId: Int64, invitationId: Int64) async throws {
        try await performGroupSpaceRequest { try await repository.revokeInvitation(
            groupSpaceId: groupSpaceId,
            invitationId: invitationId
        ) }
    }
    func preview(type: GroupInvitationCredentialKind, credential: String) async throws -> GroupInvitationPreview {
        let response = try await performGroupSpaceRequest { try await repository.previewInvitation(.init(credentialType: type.previewType, credential: credential)) }
        return .init(name: response.name, emoji: response.emoji, memberCount: response.memberCount, memberPreviews: response.memberPreviews?.map { .init(nickname: $0.nickname) }, expiresAt: response.expiresAt)
    }
    func accept(type: GroupInvitationCredentialKind, credential: String, nickname: String) async throws -> GroupInvitationAcceptanceResult {
        let response = try await performGroupSpaceRequest { try await repository.acceptInvitation(.init(credentialType: type.acceptanceType, credential: credential, nickname: nickname)) }
        return .init(joinResult: .init(response.joinResult), groupSpaceName: response.groupSpace.name, id: response.groupSpace.id)
    }
}

private extension GroupInvitationJoinResult {
    init(_ dto: GroupInvitationJoinResultDTO) {
        switch dto {
        case .joined: self = .joined
        case .alreadyMember: self = .alreadyMember
        case .rejoined: self = .rejoined
        }
    }
}
