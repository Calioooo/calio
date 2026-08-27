import Foundation

protocol GroupSpaceRepository {
    func fetchGroupSpaces() async throws -> GroupSpaceListResponseDTO
    func createGroupSpace(_ request: CreateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO
    func updateGroupSpace(groupSpaceId: Int64, request: UpdateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO
    func deleteGroupSpace(groupSpaceId: Int64) async throws
    func fetchMembers(groupSpaceId: Int64) async throws -> GroupMemberListResponseDTO
    func transferOwnership(groupSpaceId: Int64, request: TransferGroupOwnerRequestDTO) async throws -> TransferGroupOwnerResponseDTO
    func leaveGroupSpace(groupSpaceId: Int64) async throws
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws
    func issueInvitation(groupSpaceId: Int64) async throws -> GroupInvitationResponseDTO
    func fetchInvitations(groupSpaceId: Int64) async throws -> GroupInvitationListResponseDTO
    func revokeInvitation(groupSpaceId: Int64, invitationId: Int64) async throws
    func previewInvitation(_ request: PreviewGroupInvitationRequestDTO) async throws -> PreviewGroupInvitationResponseDTO
    func acceptInvitation(_ request: AcceptGroupInvitationRequestDTO) async throws -> AcceptGroupInvitationResponseDTO
}
