import Foundation

enum GroupMemberRoleDTO: String, Decodable {
    case owner = "OWNER"
    case member = "MEMBER"
}

enum GroupInvitationCredentialKind: Hashable {
    case linkToken
    case inviteCode

    var previewType: GroupInvitationPreviewCredentialTypeDTO {
        self == .linkToken ? .linkToken : .code
    }

    var acceptanceType: GroupInvitationAcceptanceCredentialTypeDTO {
        self == .linkToken ? .linkToken : .inviteCode
    }
}

enum GroupInvitationPreviewCredentialTypeDTO: String, Encodable {
    case linkToken = "LINK_TOKEN"
    case code = "CODE"
}

enum GroupInvitationAcceptanceCredentialTypeDTO: String, Encodable {
    case linkToken = "LINK_TOKEN"
    case inviteCode = "INVITE_CODE"
}

enum GroupInvitationJoinResultDTO: String, Decodable {
    case joined = "JOINED"
    case alreadyMember = "ALREADY_MEMBER"
    case rejoined = "REJOINED"
}

struct GroupMembershipResponseDTO: Decodable {
    let nickname: String
    let role: GroupMemberRoleDTO
    let createdAt: Date
    let updatedAt: Date
    let statusChangedAt: Date
}

struct GroupSpaceResponseDTO: Decodable {
    let groupSpaceId: Int64
    let name: String
    let emoji: String?
    let memberCount: Int
    let myMembership: GroupMembershipResponseDTO
    let createdAt: Date
    let updatedAt: Date
}

struct GroupSpaceListResponseDTO: Decodable {
    let groupSpaces: [GroupSpaceResponseDTO]
}

struct GroupMemberResponseDTO: Decodable {
    let memberId: Int64
    let nickname: String
    let role: GroupMemberRoleDTO
}

struct GroupMemberListResponseDTO: Decodable {
    let members: [GroupMemberResponseDTO]
}

struct CreateGroupSpaceRequestDTO: Encodable {
    let name: String
    let emoji: String?
    let nickname: String
}

struct UpdateGroupSpaceRequestDTO: Encodable {
    let name: String
    let emoji: String?
}

struct TransferGroupOwnerRequestDTO: Encodable {
    let targetMemberId: Int64
}

struct TransferGroupOwnerResponseDTO: Decodable {
    let previousOwner: GroupMemberResponseDTO
    let owner: GroupMemberResponseDTO
}

struct GroupInvitationResponseDTO: Decodable {
    let invitationId: Int64
    let inviteUrl: String
    let inviteCode: String
    let expiresAt: Date
}

struct GroupInvitationSummaryResponseDTO: Decodable {
    let invitationId: Int64
    let expiresAt: Date
}

struct GroupInvitationListResponseDTO: Decodable {
    let invitations: [GroupInvitationSummaryResponseDTO]
}

struct PreviewGroupInvitationRequestDTO: Encodable {
    let credentialType: GroupInvitationPreviewCredentialTypeDTO
    let credential: String
}

struct GroupInvitationMemberPreviewDTO: Decodable {
    let nickname: String
}

struct PreviewGroupInvitationResponseDTO: Decodable {
    let name: String
    let emoji: String?
    let memberCount: Int
    let memberPreviews: [GroupInvitationMemberPreviewDTO]?
    let expiresAt: Date

    init(
        name: String,
        emoji: String?,
        memberCount: Int,
        memberPreviews: [GroupInvitationMemberPreviewDTO]? = nil,
        expiresAt: Date
    ) {
        self.name = name
        self.emoji = emoji
        self.memberCount = memberCount
        self.memberPreviews = memberPreviews
        self.expiresAt = expiresAt
    }
}

struct AcceptGroupInvitationRequestDTO: Encodable {
    let credentialType: GroupInvitationAcceptanceCredentialTypeDTO
    let credential: String
    let nickname: String
}

struct AcceptGroupInvitationResponseDTO: Decodable {
    let joinResult: GroupInvitationJoinResultDTO
    let groupSpace: GroupSpaceJoinResponseDTO
    let membership: GroupMemberResponseDTO
}

struct GroupSpaceJoinResponseDTO: Decodable {
    let id: Int64
    let name: String
    let emoji: String?
    let myMembership: GroupMemberResponseDTO
    let memberCount: Int
    let createdAt: Date
}

extension AcceptGroupInvitationResponseDTO: Identifiable {
    var id: Int64 {
        groupSpace.id
    }
}
