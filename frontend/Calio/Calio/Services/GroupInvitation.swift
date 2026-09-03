import Foundation

enum GroupInvitationJoinResult {
    case joined
    case alreadyMember
    case rejoined
}

struct IssuedGroupInvitation {
    let id: Int64
    let url: String
    let code: String
    let expiresAt: Date
}

struct GroupInvitationSummary {
    let id: Int64
    let expiresAt: Date
}

struct GroupInvitationMemberPreview {
    let nickname: String
}

struct GroupInvitationPreview {
    let name: String
    let emoji: String?
    let memberCount: Int
    let memberPreviews: [GroupInvitationMemberPreview]?
    let expiresAt: Date
}

struct GroupInvitationAcceptanceResult: Identifiable {
    let joinResult: GroupInvitationJoinResult
    let groupSpaceName: String
    let id: Int64
}
