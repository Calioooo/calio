import Foundation

struct URLSessionGroupSpaceRepository: GroupSpaceRepository {
    private let apiClient: APIClient
    init(baseURL: URL = CalioAPIConfig.baseURL, session: URLSession = .shared, jsonDecoder: JSONDecoder = APIJSONCoding.makeDecoder(), jsonEncoder: JSONEncoder = APIJSONCoding.makeEncoder(), authTokenProvider: AuthTokenProvider? = KeychainAuthTokenStore.shared) {
        apiClient = APIClient(baseURL: baseURL, session: session, jsonDecoder: jsonDecoder, jsonEncoder: jsonEncoder, authTokenProvider: authTokenProvider)
    }
    func fetchGroupSpaces() async throws -> GroupSpaceListResponseDTO { try await apiClient.send(GroupSpaceListResponseDTO.self, method: .get, pathComponents: ["api", "group-spaces"], authorization: .bearer) }
    func createGroupSpace(_ request: CreateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { try await apiClient.send(GroupSpaceResponseDTO.self, method: .post, pathComponents: ["api", "group-spaces"], authorization: .bearer, body: request) }
    func updateGroupSpace(groupSpaceId: Int64, request: UpdateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { try await apiClient.send(GroupSpaceResponseDTO.self, method: .patch, pathComponents: ["api", "group-spaces", String(groupSpaceId)], authorization: .bearer, body: request) }
    func deleteGroupSpace(groupSpaceId: Int64) async throws { try await apiClient.sendWithoutResponse(method: .delete, pathComponents: ["api", "group-spaces", String(groupSpaceId)], authorization: .bearer) }
    func fetchMembers(groupSpaceId: Int64) async throws -> GroupMemberListResponseDTO { try await apiClient.send(GroupMemberListResponseDTO.self, method: .get, pathComponents: ["api", "group-spaces", String(groupSpaceId), "members"], authorization: .bearer) }
    func transferOwnership(groupSpaceId: Int64, request: TransferGroupOwnerRequestDTO) async throws -> TransferGroupOwnerResponseDTO { try await apiClient.send(TransferGroupOwnerResponseDTO.self, method: .post, pathComponents: ["api", "group-spaces", String(groupSpaceId), "owner-transfer"], authorization: .bearer, body: request) }
    func leaveGroupSpace(groupSpaceId: Int64) async throws { try await apiClient.sendWithoutResponse(method: .delete, pathComponents: ["api", "group-spaces", String(groupSpaceId), "members", "me"], authorization: .bearer) }
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws { try await apiClient.sendWithoutResponse(method: .delete, pathComponents: ["api", "group-spaces", String(groupSpaceId), "members", String(memberId)], authorization: .bearer) }
    func issueInvitation(groupSpaceId: Int64) async throws -> GroupInvitationResponseDTO { try await apiClient.send(GroupInvitationResponseDTO.self, method: .post, pathComponents: ["api", "group-spaces", String(groupSpaceId), "invitations"], authorization: .bearer) }
    func fetchInvitations(groupSpaceId: Int64) async throws -> GroupInvitationListResponseDTO { try await apiClient.send(GroupInvitationListResponseDTO.self, method: .get, pathComponents: ["api", "group-spaces", String(groupSpaceId), "invitations"], authorization: .bearer) }
    func revokeInvitation(groupSpaceId: Int64, invitationId: Int64) async throws { try await apiClient.sendWithoutResponse(method: .delete, pathComponents: ["api", "group-spaces", String(groupSpaceId), "invitations", String(invitationId)], authorization: .bearer) }
    func previewInvitation(_ request: PreviewGroupInvitationRequestDTO) async throws -> PreviewGroupInvitationResponseDTO { try await apiClient.send(PreviewGroupInvitationResponseDTO.self, method: .post, pathComponents: ["api", "group-invitations", "preview"], authorization: .bearer, body: request) }
    func acceptInvitation(_ request: AcceptGroupInvitationRequestDTO) async throws -> AcceptGroupInvitationResponseDTO { try await apiClient.send(AcceptGroupInvitationResponseDTO.self, method: .post, pathComponents: ["api", "group-invitations", "accept"], authorization: .bearer, body: request) }
}
