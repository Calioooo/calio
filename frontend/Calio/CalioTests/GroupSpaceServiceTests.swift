import Foundation
import Testing
@testable import Calio

@Suite(.serialized)
struct GroupSpaceServiceTests {
    @Test func serviceMapsGroupSpaceDTOsToAppModels() async throws {
        let repository = GroupSpaceRepositoryStub(fetchResponses: [[GroupSpaceRepositoryStub.sampleSpace()]])
        let service = GroupSpaceService(repository: repository)

        let spaces = try await service.fetchGroupSpaces()
        let members = try await service.members(groupSpaceId: 7)
        let transfer = try await service.transferOwnership(groupSpaceId: 7, targetMemberId: 11)

        #expect(spaces == [GroupSpaceRepositoryStub.sampleGroupSpace()])
        #expect(members == [
            GroupMember(memberId: 10, nickname: "준하", role: .owner),
            GroupMember(memberId: 11, nickname: "민지", role: .member)
        ])
        #expect(transfer.previousOwner.role == .member)
        #expect(transfer.owner.role == .owner)
    }

    @Test func serviceForwardsLifecycleAndMembershipOperationsToRepository() async throws {
        let repository = GroupSpaceRepositoryStub()
        let service = GroupSpaceService(repository: repository)

        let created = try await service.create(name: "프로젝트 팀", emoji: nil, nickname: "준하")
        let updated = try await service.update(groupSpaceId: 7, name: "새 프로젝트 팀", emoji: nil)
        let members = try await service.members(groupSpaceId: 7)
        _ = try await service.transferOwnership(groupSpaceId: 7, targetMemberId: 11)
        try await service.removeMember(groupSpaceId: 7, memberId: 11)
        try await service.leave(groupSpaceId: 7)
        try await service.delete(groupSpaceId: 7)

        #expect(created.name == "프로젝트 팀")
        #expect(created.myMembership.role == .owner)
        #expect(updated.name == "새 프로젝트 팀")
        #expect(members.map(\.memberId) == [10, 11])
        #expect(repository.operations == ["create", "update:7", "members:7", "transfer:7:11", "remove:7:11", "leave:7", "delete:7"])
    }

    @MainActor @Test func listViewModelPreservesConfirmedSpacesWhenRefreshFails() async {
        let confirmedSpace = GroupSpaceRepositoryStub.sampleSpace(name: "확정된 공간")
        let repository = GroupSpaceRepositoryStub(fetchResponses: [[confirmedSpace], []], fetchErrorOnCall: 2)
        let viewModel = GroupSpaceListViewModel(service: GroupSpaceService(repository: repository))

        await viewModel.load()
        await viewModel.load()

        #expect(viewModel.spaces.map(\.name) == ["확정된 공간"])
        #expect(viewModel.errorMessage == "그룹 공간을 불러오지 못했습니다. 다시 시도해 주세요.")
    }

    @MainActor @Test func ownershipTransferAppliesBackendConfirmedRolesToDetailState() async throws {
        let repository = GroupSpaceRepositoryStub()
        let viewModel = GroupSpaceDetailViewModel(
            groupSpace: GroupSpaceRepositoryStub.sampleGroupSpace(),
            service: GroupSpaceService(repository: repository)
        )

        await viewModel.loadMembers()
        let target = try #require(viewModel.members.first { $0.memberId == 11 })
        let succeeded = await viewModel.transferOwnership(to: target)

        #expect(succeeded)
        #expect(viewModel.groupSpace.myMembership.role == .member)
        #expect(viewModel.members.first { $0.memberId == 10 }?.role == .member)
        #expect(viewModel.members.first { $0.memberId == 11 }?.role == .owner)
    }

}

private final class GroupSpaceRepositoryStub: GroupSpaceRepository {
    static let date = Date(timeIntervalSince1970: 0)
    var operations: [String] = []
    private var fetchResponses: [[GroupSpaceResponseDTO]]
    private let fetchErrorOnCall: Int?
    private var fetchCallCount = 0

    init(
        fetchResponses: [[GroupSpaceResponseDTO]] = [],
        fetchErrorOnCall: Int? = nil
    ) {
        self.fetchResponses = fetchResponses
        self.fetchErrorOnCall = fetchErrorOnCall
    }

    static func sampleSpace(name: String = "프로젝트 팀") -> GroupSpaceResponseDTO {
        .init(groupSpaceId: 7, name: name, emoji: nil, memberCount: 2,
              myMembership: .init(nickname: "준하", role: .owner, createdAt: date, updatedAt: date, statusChangedAt: date),
              createdAt: date, updatedAt: date)
    }

    static func sampleGroupSpace(name: String = "프로젝트 팀") -> GroupSpace {
        GroupSpace(
            groupSpaceId: 7,
            name: name,
            emoji: nil,
            memberCount: 2,
            myMembership: GroupMembership(
                nickname: "준하",
                role: .owner,
                createdAt: date,
                updatedAt: date,
                statusChangedAt: date
            ),
            createdAt: date,
            updatedAt: date
        )
    }

    func fetchGroupSpaces() async throws -> GroupSpaceListResponseDTO {
        fetchCallCount += 1
        if fetchErrorOnCall == fetchCallCount { throw StubError.failed }
        return .init(groupSpaces: fetchResponses.isEmpty ? [] : fetchResponses.removeFirst())
    }
    func createGroupSpace(_ request: CreateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { operations.append("create"); return Self.sampleSpace(name: request.name) }
    func updateGroupSpace(groupSpaceId: Int64, request: UpdateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { operations.append("update:\(groupSpaceId)"); return Self.sampleSpace(name: request.name) }
    func deleteGroupSpace(groupSpaceId: Int64) async throws { operations.append("delete:\(groupSpaceId)") }
    func fetchMembers(groupSpaceId: Int64) async throws -> GroupMemberListResponseDTO { operations.append("members:\(groupSpaceId)"); return .init(members: [.init(memberId: 10, nickname: "준하", role: .owner), .init(memberId: 11, nickname: "민지", role: .member)]) }
    func transferOwnership(groupSpaceId: Int64, request: TransferGroupOwnerRequestDTO) async throws -> TransferGroupOwnerResponseDTO { operations.append("transfer:\(groupSpaceId):\(request.targetMemberId)"); return .init(previousOwner: .init(memberId: 10, nickname: "준하", role: .member), owner: .init(memberId: request.targetMemberId, nickname: "민지", role: .owner)) }
    func leaveGroupSpace(groupSpaceId: Int64) async throws { operations.append("leave:\(groupSpaceId)") }
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws { operations.append("remove:\(groupSpaceId):\(memberId)") }

    enum StubError: Error { case failed }
}
