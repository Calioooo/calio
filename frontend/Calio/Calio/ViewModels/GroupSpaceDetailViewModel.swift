import Combine
import Foundation

@MainActor final class GroupSpaceDetailViewModel: ObservableObject {
    @Published private(set) var groupSpace: GroupSpaceResponseDTO
    @Published private(set) var members: [GroupMemberResponseDTO] = []
    @Published private(set) var isLoading = false
    @Published var errorMessage: String?

    private let service: GroupSpaceService

    init(groupSpace: GroupSpaceResponseDTO, service: GroupSpaceService = GroupSpaceService()) {
        self.groupSpace = groupSpace
        self.service = service
    }

    func loadMembers() async {
        isLoading = true
        defer { isLoading = false }
        do {
            members = try await service.members(groupSpaceId: groupSpace.groupSpaceId)
            errorMessage = nil
        } catch {
            errorMessage = "멤버 정보를 불러오지 못했습니다. 다시 시도해 주세요."
        }
    }

    func update(name: String) async -> Bool {
        do {
            groupSpace = try await service.update(groupSpaceId: groupSpace.groupSpaceId, name: name, emoji: groupSpace.emoji)
            errorMessage = nil
            return true
        } catch {
            errorMessage = "그룹 공간을 수정하지 못했습니다. 다시 시도해 주세요."
            return false
        }
    }

    func delete() async -> Bool {
        do {
            try await service.delete(groupSpaceId: groupSpace.groupSpaceId)
            return true
        } catch {
            errorMessage = "그룹 공간을 삭제하지 못했습니다. 다시 시도해 주세요."
            return false
        }
    }

    func leave() async -> Bool {
        do {
            try await service.leave(groupSpaceId: groupSpace.groupSpaceId)
            return true
        } catch {
            errorMessage = "그룹 공간에서 나가지 못했습니다. 다시 시도해 주세요."
            return false
        }
    }

    func remove(member: GroupMemberResponseDTO) async {
        do {
            try await service.removeMember(groupSpaceId: groupSpace.groupSpaceId, memberId: member.memberId)
            members.removeAll { $0.memberId == member.memberId }
        } catch {
            errorMessage = "멤버를 내보내지 못했습니다. 다시 시도해 주세요."
        }
    }

    func transferOwnership(to member: GroupMemberResponseDTO) async -> Bool {
        do {
            let result = try await service.transferOwnership(
                groupSpaceId: groupSpace.groupSpaceId,
                targetMemberId: member.memberId
            )
            applyOwnershipTransfer(result)
            errorMessage = nil
            return true
        } catch {
            errorMessage = "소유권을 이전하지 못했습니다. 다시 시도해 주세요."
            return false
        }
    }

    func clearError() { errorMessage = nil }

    private func applyOwnershipTransfer(_ result: TransferGroupOwnerResponseDTO) {
        members = members.map { member in
            if member.memberId == result.previousOwner.memberId {
                return result.previousOwner
            }
            if member.memberId == result.owner.memberId {
                return result.owner
            }
            return member
        }

        let membership = groupSpace.myMembership
        groupSpace = GroupSpaceResponseDTO(
            groupSpaceId: groupSpace.groupSpaceId,
            name: groupSpace.name,
            emoji: groupSpace.emoji,
            memberCount: groupSpace.memberCount,
            myMembership: GroupMembershipResponseDTO(
                nickname: membership.nickname,
                role: result.previousOwner.role,
                createdAt: membership.createdAt,
                updatedAt: membership.updatedAt,
                statusChangedAt: membership.statusChangedAt
            ),
            createdAt: groupSpace.createdAt,
            updatedAt: groupSpace.updatedAt
        )
    }
}
