import Combine
import Foundation

@MainActor final class GroupSpaceDetailViewModel: ObservableObject {
    @Published private(set) var groupSpace: GroupSpace
    @Published private(set) var members: [GroupMember] = []
    @Published private(set) var isLoading = false
    @Published private(set) var failure: GroupSpaceFailure?
    @Published private(set) var failedOperation: GroupSpaceOperation?
    @Published var errorMessage: String?
    @Published private(set) var postRemovalRefreshMessage: String?

    private let service: GroupSpaceService

    init(groupSpace: GroupSpace, service: GroupSpaceService = GroupSpaceService()) {
        self.groupSpace = groupSpace
        self.service = service
    }

    deinit {}

    func loadMembers() async {
        isLoading = true
        defer { isLoading = false }
        do {
            members = try await service.members(groupSpaceId: groupSpace.groupSpaceId)
            clearFailure()
        } catch is CancellationError {
            return
        } catch {
            recordFailure(error, for: .loadMembers)
        }
    }

    func update(name: String) async -> Bool {
        do {
            groupSpace = try await service.update(groupSpaceId: groupSpace.groupSpaceId, name: name, emoji: groupSpace.emoji)
            clearFailure()
            return true
        } catch is CancellationError {
            return false
        } catch {
            recordFailure(error, for: .update)
            return false
        }
    }

    func delete() async -> Bool {
        do {
            try await service.delete(groupSpaceId: groupSpace.groupSpaceId)
            clearFailure()
            return true
        } catch is CancellationError {
            return false
        } catch {
            recordFailure(error, for: .delete)
            return false
        }
    }

    func leave() async -> Bool {
        do {
            try await service.leave(groupSpaceId: groupSpace.groupSpaceId)
            clearFailure()
            return true
        } catch is CancellationError {
            return false
        } catch {
            recordFailure(error, for: .leave)
            return false
        }
    }

    func remove(member: GroupMember) async -> Bool {
        postRemovalRefreshMessage = nil
        do {
            try await service.removeMember(groupSpaceId: groupSpace.groupSpaceId, memberId: member.memberId)
            members.removeAll { $0.memberId == member.memberId }
            do {
                groupSpace = try await service.fetchGroupSpace(groupSpaceId: groupSpace.groupSpaceId)
            } catch is CancellationError {
                postRemovalRefreshMessage = "멤버를 내보냈지만 최신 그룹 정보를 불러오지 못했습니다."
            } catch {
                postRemovalRefreshMessage = "멤버를 내보냈지만 최신 그룹 정보를 불러오지 못했습니다."
            }
            clearFailure()
            return true
        } catch is CancellationError {
            return false
        } catch {
            recordFailure(error, for: .removeMember)
            return false
        }
    }

    func transferOwnership(to member: GroupMember) async -> Bool {
        do {
            let result = try await service.transferOwnership(
                groupSpaceId: groupSpace.groupSpaceId,
                targetMemberId: member.memberId
            )
            applyOwnershipTransfer(result)
            clearFailure()
            return true
        } catch is CancellationError {
            return false
        } catch {
            recordFailure(error, for: .transferOwnership)
            return false
        }
    }

    func clearError() { clearFailure() }
    func clearPostRemovalRefreshMessage() { postRemovalRefreshMessage = nil }

    private func recordFailure(_ error: Error, for operation: GroupSpaceOperation) {
        let failure = error as? GroupSpaceFailure ?? .unexpected
        self.failure = failure
        failedOperation = operation
        errorMessage = failure.message(for: operation)
    }

    private func clearFailure() {
        failure = nil
        failedOperation = nil
        errorMessage = nil
    }

    private func applyOwnershipTransfer(_ result: GroupOwnershipTransfer) {
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
        groupSpace = GroupSpace(
            groupSpaceId: groupSpace.groupSpaceId,
            name: groupSpace.name,
            emoji: groupSpace.emoji,
            memberCount: groupSpace.memberCount,
            myMembership: GroupMembership(
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
