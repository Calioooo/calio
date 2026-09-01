import Foundation

struct GroupSpaceService {
    private let repository: GroupSpaceRepository
    init(repository: GroupSpaceRepository = URLSessionGroupSpaceRepository()) {
        self.repository = repository
    }
    func fetchGroupSpaces() async throws -> [GroupSpace] {
        let response = try await perform { try await repository.fetchGroupSpaces() }
        return response.groupSpaces.map(mapToGroupSpace(_:))
    }
    func fetchGroupSpace(groupSpaceId: Int64) async throws -> GroupSpace {
        let response = try await perform { try await repository.fetchGroupSpace(groupSpaceId: groupSpaceId) }
        return mapToGroupSpace(response)
    }
    func create(name: String, emoji: String?, nickname: String) async throws -> GroupSpace {
        let response = try await perform { try await repository.createGroupSpace(.init(name: name, emoji: emoji, nickname: nickname)) }
        return mapToGroupSpace(response)
    }
    func update(groupSpaceId: Int64, name: String, emoji: String?) async throws -> GroupSpace {
        let response = try await perform { try await repository.updateGroupSpace(groupSpaceId: groupSpaceId, request: .init(name: name, emoji: emoji)) }
        return mapToGroupSpace(response)
    }
    func delete(groupSpaceId: Int64) async throws {
        try await perform { try await repository.deleteGroupSpace(groupSpaceId: groupSpaceId) }
    }
    func members(groupSpaceId: Int64) async throws -> [GroupMember] {
        let response = try await perform { try await repository.fetchMembers(groupSpaceId: groupSpaceId) }
        return response.members.map(mapToGroupMember(_:))
    }
    func leave(groupSpaceId: Int64) async throws {
        try await perform { try await repository.leaveGroupSpace(groupSpaceId: groupSpaceId) }
    }
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws {
        try await perform { try await repository.removeMember(groupSpaceId: groupSpaceId, memberId: memberId) }
    }
    func transferOwnership(groupSpaceId: Int64, targetMemberId: Int64) async throws -> GroupOwnershipTransfer {
        let response = try await perform { try await repository.transferOwnership(groupSpaceId: groupSpaceId, request: .init(targetMemberId: targetMemberId)) }
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

    private func perform<T>(_ operation: () async throws -> T) async throws -> T {
        do {
            return try await operation()
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as APIError {
            throw GroupSpaceFailure(apiError: error)
        } catch {
            throw GroupSpaceFailure.unexpected
        }
    }
}

enum GroupSpaceFailure: Error, Equatable {
    case network
    case decoding
    case backend(errorCode: String?)
    case unexpected

    init(apiError: APIError) {
        switch apiError {
        case .network:
            self = .network
        case .decoding:
            self = .decoding
        case .backend(_, let problem):
            self = .backend(errorCode: problem?.errorCode)
        case .invalidRequest, .invalidResponse, .encoding, .unexpected:
            self = .unexpected
        }
    }
}

enum GroupSpaceOperation: Equatable {
    case load, create, update, delete, leave, removeMember, transferOwnership, loadMembers

    var failureAction: String {
        switch self {
        case .load: "그룹 공간을 불러오지"
        case .create: "그룹 공간을 만들지"
        case .update: "그룹 공간을 수정하지"
        case .delete: "그룹 공간을 삭제하지"
        case .leave: "그룹 공간에서 나가지"
        case .removeMember: "멤버를 내보내지"
        case .transferOwnership: "소유권을 이전하지"
        case .loadMembers: "멤버 정보를 불러오지"
        }
    }
}

extension GroupSpaceFailure {
    func message(for operation: GroupSpaceOperation) -> String {
        switch self {
        case .network:
            return "\(operation.failureAction) 못했습니다. 네트워크 연결을 확인해 주세요."
        case .decoding:
            return "\(operation.failureAction) 못했습니다. 응답을 처리하지 못했습니다."
        case .backend(let errorCode):
            guard let errorCode else { return "\(operation.failureAction) 못했습니다. 서버 요청이 거절되었습니다." }
            return "\(operation.failureAction) 못했습니다. (\(errorCode))"
        case .unexpected:
            return "\(operation.failureAction) 못했습니다. 다시 시도해 주세요."
        }
    }
}
