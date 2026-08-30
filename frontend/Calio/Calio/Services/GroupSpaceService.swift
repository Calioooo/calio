import Foundation

struct GroupSpaceService {
    private let repository: GroupSpaceRepository
    init(repository: GroupSpaceRepository = URLSessionGroupSpaceRepository()) { self.repository = repository }
    func fetchGroupSpaces() async throws -> [GroupSpace] {
        try await repository.fetchGroupSpaces().groupSpaces.map(mapToGroupSpace(_:))
    }
    func fetchGroupSpace(groupSpaceId: Int64) async throws -> GroupSpace {
        mapToGroupSpace(try await repository.fetchGroupSpace(groupSpaceId: groupSpaceId))
    }
    func create(name: String, emoji: String?, nickname: String) async throws -> GroupSpace {
        mapToGroupSpace(try await repository.createGroupSpace(.init(name: name, emoji: emoji, nickname: nickname)))
    }
    func update(groupSpaceId: Int64, name: String, emoji: String?) async throws -> GroupSpace {
        mapToGroupSpace(try await repository.updateGroupSpace(groupSpaceId: groupSpaceId, request: .init(name: name, emoji: emoji)))
    }
    func delete(groupSpaceId: Int64) async throws {
        try await repository.deleteGroupSpace(groupSpaceId: groupSpaceId)
    }
    func members(groupSpaceId: Int64) async throws -> [GroupMember] {
        try await repository.fetchMembers(groupSpaceId: groupSpaceId).members.map(mapToGroupMember(_:))
    }
    func leave(groupSpaceId: Int64) async throws {
        try await repository.leaveGroupSpace(groupSpaceId: groupSpaceId)
    }
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws {
        try await repository.removeMember(groupSpaceId: groupSpaceId, memberId: memberId)
    }
    func transferOwnership(groupSpaceId: Int64, targetMemberId: Int64) async throws -> GroupOwnershipTransfer {
        let response = try await repository.transferOwnership(groupSpaceId: groupSpaceId, request: .init(targetMemberId: targetMemberId))
        return GroupOwnershipTransfer(
            previousOwner: mapToGroupMember(response.previousOwner),
            owner: mapToGroupMember(response.owner)
        )
    }

    private func mapToGroupSpace(_ dto: GroupSpaceResponseDTO) -> GroupSpace {
        GroupSpace(
            groupSpaceId: dto.groupSpaceId,
            name: dto.name,
            emoji: dto.emoji,
            memberCount: dto.memberCount,
            myMembership: GroupMembership(
                nickname: dto.myMembership.nickname,
                role: mapToGroupMemberRole(dto.myMembership.role),
                createdAt: dto.myMembership.createdAt,
                updatedAt: dto.myMembership.updatedAt,
                statusChangedAt: dto.myMembership.statusChangedAt
            ),
            createdAt: dto.createdAt,
            updatedAt: dto.updatedAt
        )
    }

    private func mapToGroupMember(_ dto: GroupMemberResponseDTO) -> GroupMember {
        GroupMember(memberId: dto.memberId, nickname: dto.nickname, role: mapToGroupMemberRole(dto.role))
    }

    private func mapToGroupMemberRole(_ dto: GroupMemberRoleDTO) -> GroupMemberRole {
        switch dto {
        case .owner: return .owner
        case .member: return .member
        }
    }
}
