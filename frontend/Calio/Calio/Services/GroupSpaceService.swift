import Foundation

struct GroupSpaceService {
    private let repository: GroupSpaceRepository
    init(repository: GroupSpaceRepository = URLSessionGroupSpaceRepository()) { self.repository = repository }
    func fetchGroupSpaces() async throws -> [GroupSpaceResponseDTO] { try await repository.fetchGroupSpaces().groupSpaces }
    func create(name: String, emoji: String?, nickname: String) async throws -> GroupSpaceResponseDTO {
        try await repository.createGroupSpace(.init(name: name, emoji: emoji, nickname: nickname))
    }
    func update(groupSpaceId: Int64, name: String, emoji: String?) async throws -> GroupSpaceResponseDTO {
        try await repository.updateGroupSpace(groupSpaceId: groupSpaceId, request: .init(name: name, emoji: emoji))
    }
    func delete(groupSpaceId: Int64) async throws {
        try await repository.deleteGroupSpace(groupSpaceId: groupSpaceId)
    }
    func members(groupSpaceId: Int64) async throws -> [GroupMemberResponseDTO] {
        try await repository.fetchMembers(groupSpaceId: groupSpaceId).members
    }
    func leave(groupSpaceId: Int64) async throws {
        try await repository.leaveGroupSpace(groupSpaceId: groupSpaceId)
    }
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws {
        try await repository.removeMember(groupSpaceId: groupSpaceId, memberId: memberId)
    }
    func transferOwnership(groupSpaceId: Int64, targetMemberId: Int64) async throws -> TransferGroupOwnerResponseDTO {
        try await repository.transferOwnership(groupSpaceId: groupSpaceId, request: .init(targetMemberId: targetMemberId))
    }
}
