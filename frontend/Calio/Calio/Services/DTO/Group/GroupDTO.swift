import Foundation

enum GroupMemberRoleDTO: String, Decodable {
    case owner = "OWNER"
    case member = "MEMBER"
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
