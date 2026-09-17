import Foundation

enum GroupMemberRole: Equatable {
    case owner
    case member
}

struct GroupMembership: Equatable {
    let nickname: String
    let role: GroupMemberRole
    let createdAt: Date
    let updatedAt: Date
    let statusChangedAt: Date
}

struct GroupSpace: Identifiable, Equatable {
    let groupSpaceId: Int64
    let name: String
    let emoji: String?
    let memberCount: Int
    let myMembership: GroupMembership
    let createdAt: Date
    let updatedAt: Date

    var id: Int64 { groupSpaceId }

    var canManageGroupSpace: Bool { myMembership.role == .owner }
}

struct GroupMember: Identifiable, Equatable {
    let memberId: Int64
    let nickname: String
    let role: GroupMemberRole

    var id: Int64 { memberId }
}

struct GroupOwnershipTransfer: Equatable {
    let previousOwner: GroupMember
    let owner: GroupMember
}
