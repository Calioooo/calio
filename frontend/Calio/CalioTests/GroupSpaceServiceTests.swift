import Foundation
import Testing
@testable import Calio

@Suite(.serialized)
struct GroupSpaceServiceTests {
    @Test func servicePreservesNetworkDecodingAndBackendFailures() async {
        await expectFetchFailure(
            .network(URLError(.notConnectedToInternet)),
            equals: .network
        )
        await expectFetchFailure(
            .decoding(DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "invalid response"))),
            equals: .decoding
        )
        await expectFetchFailure(
            .backend(
                statusCode: 409,
                problem: .init(type: nil, title: "CONFLICT", status: 409, detail: nil, errorCode: "GROUP_SPACE_CONFLICT")
            ),
            equals: .backend(errorCode: "GROUP_SPACE_CONFLICT")
        )
    }

    @Test func servicePreservesTaskCancellation() async {
        let service = GroupSpaceService(repository: CancelledGroupSpaceRepository())

        do {
            _ = try await service.fetchGroupSpaces()
            Issue.record("Expected task cancellation")
        } catch is CancellationError {
        } catch {
            Issue.record("Unexpected failure: \(error)")
        }
    }

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

    @MainActor @Test func listViewModelIgnoresAnOlderLoadThatFinishesAfterTheLatestLoad() async {
        let repository = OutOfOrderGroupSpaceRepository()
        let viewModel = GroupSpaceListViewModel(service: GroupSpaceService(repository: repository))

        let firstLoad = Task { await viewModel.load() }
        while await repository.startedFetchCount() == 0 { await Task.yield() }
        let secondLoad = Task { await viewModel.load() }

        await firstLoad.value
        await secondLoad.value

        #expect(viewModel.spaces.map(\.name) == ["최신 공간"])
        #expect(!viewModel.isLoading)
        #expect(!viewModel.didFailLoading)
        #expect(viewModel.failure == nil)
    }

    @MainActor @Test func listViewModelDoesNotPublishFailureWhenLoadingIsCancelled() async {
        let repository = CancellableGroupSpaceRepository()
        let viewModel = GroupSpaceListViewModel(service: GroupSpaceService(repository: repository))

        let load = Task { await viewModel.load() }
        while await repository.startedFetchCount() == 0 { await Task.yield() }
        load.cancel()
        await load.value

        #expect(!viewModel.isLoading)
        #expect(viewModel.failure == nil)
        #expect(viewModel.failedOperation == nil)
        #expect(viewModel.errorMessage == nil)
    }

    @MainActor @Test func listViewModelShowsInitialFailureThenRecoversOnRetry() async {
        let confirmedSpace = GroupSpaceRepositoryStub.sampleSpace()
        let repository = GroupSpaceRepositoryStub(fetchResponses: [[confirmedSpace]], fetchErrorOnCall: 1)
        let viewModel = GroupSpaceListViewModel(service: GroupSpaceService(repository: repository))

        await viewModel.load()

        #expect(viewModel.didFailLoading)
        #expect(viewModel.failure == .unexpected)
        #expect(viewModel.failedOperation == .load)

        await viewModel.load()

        #expect(viewModel.spaces.map(\.name) == ["프로젝트 팀"])
        #expect(!viewModel.didFailLoading)
        #expect(viewModel.errorMessage == nil)
    }

    @MainActor @Test func listViewModelExposesCreationFailureEvenAfterInitialLoadFailure() async {
        let viewModel = GroupSpaceListViewModel(
            service: GroupSpaceService(repository: FailingGroupSpaceRepository(error: .backend(
                statusCode: 409,
                problem: .init(type: nil, title: "CONFLICT", status: 409, detail: nil, errorCode: "GROUP_SPACE_CONFLICT")
            )))
        )

        await viewModel.load()
        let succeeded = await viewModel.create(name: "프로젝트 팀", nickname: "준하")

        #expect(!succeeded)
        #expect(viewModel.didFailLoading)
        #expect(viewModel.failure == .backend(errorCode: "GROUP_SPACE_CONFLICT"))
        #expect(viewModel.failedOperation == .create)
        #expect(viewModel.errorMessage?.contains("GROUP_SPACE_CONFLICT") == true)
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

    @MainActor @Test func removingMemberRefreshesGroupSpaceFromBackend() async throws {
        let repository = GroupSpaceRepositoryStub(memberCountAfterRemoval: 1)
        let viewModel = GroupSpaceDetailViewModel(
            groupSpace: GroupSpaceRepositoryStub.sampleGroupSpace(),
            service: GroupSpaceService(repository: repository)
        )

        await viewModel.loadMembers()
        let member = try #require(viewModel.members.first { $0.memberId == 11 })
        let succeeded = await viewModel.remove(member: member)

        #expect(succeeded)
        #expect(viewModel.groupSpace.memberCount == 1)
        #expect(viewModel.members.map(\.memberId) == [10])
        #expect(repository.operations == ["members:7", "remove:7:11", "fetch:7"])
    }

    @MainActor @Test func removingMemberRemainsSuccessfulWhenThePostRemovalRefreshFails() async throws {
        let repository = GroupSpaceRepositoryStub(fetchGroupSpaceError: .network(URLError(.notConnectedToInternet)))
        let viewModel = GroupSpaceDetailViewModel(
            groupSpace: GroupSpaceRepositoryStub.sampleGroupSpace(),
            service: GroupSpaceService(repository: repository)
        )

        await viewModel.loadMembers()
        let member = try #require(viewModel.members.first { $0.memberId == 11 })
        let succeeded = await viewModel.remove(member: member)

        #expect(succeeded)
        #expect(viewModel.members.map(\.memberId) == [10])
        #expect(viewModel.groupSpace.memberCount == 2)
        #expect(viewModel.postRemovalRefreshMessage == "멤버를 내보냈지만 최신 그룹 정보를 불러오지 못했습니다.")
        #expect(viewModel.failure == nil)
        #expect(viewModel.failedOperation == nil)
    }

    @MainActor @Test func detailViewModelRecordsOperationFailuresWithoutDiscardingBackendErrorCode() async {
        let viewModel = GroupSpaceDetailViewModel(
            groupSpace: GroupSpaceRepositoryStub.sampleGroupSpace(),
            service: GroupSpaceService(repository: FailingGroupSpaceRepository(error: .backend(
                statusCode: 403,
                problem: .init(type: nil, title: "FORBIDDEN", status: 403, detail: nil, errorCode: "GROUP_SPACE_FORBIDDEN")
            )))
        )
        let member = GroupMember(memberId: 11, nickname: "민지", role: .member)

        await viewModel.loadMembers()
        #expect(viewModel.failedOperation == .loadMembers)
        #expect(viewModel.failure == .backend(errorCode: "GROUP_SPACE_FORBIDDEN"))

        let updateSucceeded = await viewModel.update(name: "수정 팀")
        #expect(!updateSucceeded)
        #expect(viewModel.failedOperation == .update)
        let deletionSucceeded = await viewModel.delete()
        #expect(!deletionSucceeded)
        #expect(viewModel.failedOperation == .delete)
        let leaveSucceeded = await viewModel.leave()
        #expect(!leaveSucceeded)
        #expect(viewModel.failedOperation == .leave)
        let removalSucceeded = await viewModel.remove(member: member)
        #expect(!removalSucceeded)
        #expect(viewModel.failedOperation == .removeMember)
        let transferSucceeded = await viewModel.transferOwnership(to: member)
        #expect(!transferSucceeded)
        #expect(viewModel.failedOperation == .transferOwnership)
    }

    @Test func groupSpaceManagementPermissionFollowsCanonicalMembershipRole() {
        #expect(GroupSpaceRepositoryStub.sampleGroupSpace().canManageGroupSpace)
        #expect(!GroupSpaceRepositoryStub.sampleGroupSpace(role: .member).canManageGroupSpace)
    }

    private func expectFetchFailure(_ apiError: APIError, equals expected: GroupSpaceFailure) async {
        let service = GroupSpaceService(repository: FailingGroupSpaceRepository(error: apiError))

        do {
            _ = try await service.fetchGroupSpaces()
            Issue.record("Expected group space service failure")
        } catch let failure as GroupSpaceFailure {
            #expect(failure == expected)
        } catch {
            Issue.record("Unexpected failure: \(error)")
        }
    }

}

private final class GroupSpaceRepositoryStub: GroupSpaceRepository {
    static let date = Date(timeIntervalSince1970: 0)
    var operations: [String] = []
    private var fetchResponses: [[GroupSpaceResponseDTO]]
    private let fetchErrorOnCall: Int?
    private let memberCountAfterRemoval: Int
    private let fetchGroupSpaceError: APIError?
    private var fetchCallCount = 0

    init(
        fetchResponses: [[GroupSpaceResponseDTO]] = [],
        fetchErrorOnCall: Int? = nil,
        memberCountAfterRemoval: Int = 2,
        fetchGroupSpaceError: APIError? = nil
    ) {
        self.fetchResponses = fetchResponses
        self.fetchErrorOnCall = fetchErrorOnCall
        self.memberCountAfterRemoval = memberCountAfterRemoval
        self.fetchGroupSpaceError = fetchGroupSpaceError
    }

    static func sampleSpace(name: String = "프로젝트 팀") -> GroupSpaceResponseDTO {
        .init(groupSpaceId: 7, name: name, emoji: nil, memberCount: 2,
              myMembership: .init(nickname: "준하", role: .owner, createdAt: date, updatedAt: date, statusChangedAt: date),
              createdAt: date, updatedAt: date)
    }

    static func sampleGroupSpace(name: String = "프로젝트 팀", role: GroupMemberRole = .owner) -> GroupSpace {
        GroupSpace(
            groupSpaceId: 7,
            name: name,
            emoji: nil,
            memberCount: 2,
            myMembership: GroupMembership(
                nickname: "준하",
                role: role,
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
    func fetchGroupSpace(groupSpaceId: Int64) async throws -> GroupSpaceResponseDTO {
        operations.append("fetch:\(groupSpaceId)")
        if let fetchGroupSpaceError { throw fetchGroupSpaceError }
        var response = Self.sampleSpace()
        response = .init(
            groupSpaceId: response.groupSpaceId,
            name: response.name,
            emoji: response.emoji,
            memberCount: memberCountAfterRemoval,
            myMembership: response.myMembership,
            createdAt: response.createdAt,
            updatedAt: response.updatedAt
        )
        return response
    }
    func createGroupSpace(_ request: CreateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { operations.append("create"); return Self.sampleSpace(name: request.name) }
    func updateGroupSpace(groupSpaceId: Int64, request: UpdateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { operations.append("update:\(groupSpaceId)"); return Self.sampleSpace(name: request.name) }
    func deleteGroupSpace(groupSpaceId: Int64) async throws { operations.append("delete:\(groupSpaceId)") }
    func fetchMembers(groupSpaceId: Int64) async throws -> GroupMemberListResponseDTO { operations.append("members:\(groupSpaceId)"); return .init(members: [.init(memberId: 10, nickname: "준하", role: .owner), .init(memberId: 11, nickname: "민지", role: .member)]) }
    func transferOwnership(groupSpaceId: Int64, request: TransferGroupOwnerRequestDTO) async throws -> TransferGroupOwnerResponseDTO { operations.append("transfer:\(groupSpaceId):\(request.targetMemberId)"); return .init(previousOwner: .init(memberId: 10, nickname: "준하", role: .member), owner: .init(memberId: request.targetMemberId, nickname: "민지", role: .owner)) }
    func leaveGroupSpace(groupSpaceId: Int64) async throws { operations.append("leave:\(groupSpaceId)") }
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws { operations.append("remove:\(groupSpaceId):\(memberId)") }

    enum StubError: Error { case failed }

    deinit {}
}

private struct FailingGroupSpaceRepository: GroupSpaceRepository {
    let error: APIError

    func fetchGroupSpaces() async throws -> GroupSpaceListResponseDTO { throw error }
    func fetchGroupSpace(groupSpaceId: Int64) async throws -> GroupSpaceResponseDTO { throw error }
    func createGroupSpace(_ request: CreateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { throw error }
    func updateGroupSpace(groupSpaceId: Int64, request: UpdateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { throw error }
    func deleteGroupSpace(groupSpaceId: Int64) async throws { throw error }
    func fetchMembers(groupSpaceId: Int64) async throws -> GroupMemberListResponseDTO { throw error }
    func transferOwnership(groupSpaceId: Int64, request: TransferGroupOwnerRequestDTO) async throws -> TransferGroupOwnerResponseDTO { throw error }
    func leaveGroupSpace(groupSpaceId: Int64) async throws { throw error }
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws { throw error }
}

private actor OutOfOrderGroupSpaceRepository: GroupSpaceRepository {
    private var fetchCount = 0

    func startedFetchCount() -> Int { fetchCount }

    func fetchGroupSpaces() async throws -> GroupSpaceListResponseDTO {
        fetchCount += 1
        let requestNumber = fetchCount
        try await Task.sleep(for: requestNumber == 1 ? .milliseconds(50) : .milliseconds(1))
        return .init(groupSpaces: [GroupSpaceRepositoryStub.sampleSpace(name: requestNumber == 1 ? "이전 공간" : "최신 공간")])
    }

    func fetchGroupSpace(groupSpaceId: Int64) async throws -> GroupSpaceResponseDTO { fatalError("Unexpected call") }
    func createGroupSpace(_ request: CreateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { fatalError("Unexpected call") }
    func updateGroupSpace(groupSpaceId: Int64, request: UpdateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { fatalError("Unexpected call") }
    func deleteGroupSpace(groupSpaceId: Int64) async throws { fatalError("Unexpected call") }
    func fetchMembers(groupSpaceId: Int64) async throws -> GroupMemberListResponseDTO { fatalError("Unexpected call") }
    func transferOwnership(groupSpaceId: Int64, request: TransferGroupOwnerRequestDTO) async throws -> TransferGroupOwnerResponseDTO { fatalError("Unexpected call") }
    func leaveGroupSpace(groupSpaceId: Int64) async throws { fatalError("Unexpected call") }
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws { fatalError("Unexpected call") }
}

private struct CancelledGroupSpaceRepository: GroupSpaceRepository {
    func fetchGroupSpaces() async throws -> GroupSpaceListResponseDTO { throw CancellationError() }
    func fetchGroupSpace(groupSpaceId: Int64) async throws -> GroupSpaceResponseDTO { throw CancellationError() }
    func createGroupSpace(_ request: CreateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { throw CancellationError() }
    func updateGroupSpace(groupSpaceId: Int64, request: UpdateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { throw CancellationError() }
    func deleteGroupSpace(groupSpaceId: Int64) async throws { throw CancellationError() }
    func fetchMembers(groupSpaceId: Int64) async throws -> GroupMemberListResponseDTO { throw CancellationError() }
    func transferOwnership(groupSpaceId: Int64, request: TransferGroupOwnerRequestDTO) async throws -> TransferGroupOwnerResponseDTO { throw CancellationError() }
    func leaveGroupSpace(groupSpaceId: Int64) async throws { throw CancellationError() }
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws { throw CancellationError() }
}

private actor CancellableGroupSpaceRepository: GroupSpaceRepository {
    private var fetchCount = 0

    func startedFetchCount() -> Int { fetchCount }

    func fetchGroupSpaces() async throws -> GroupSpaceListResponseDTO {
        fetchCount += 1
        try await Task.sleep(for: .seconds(1))
        return .init(groupSpaces: [])
    }

    func fetchGroupSpace(groupSpaceId: Int64) async throws -> GroupSpaceResponseDTO { fatalError("Unexpected call") }
    func createGroupSpace(_ request: CreateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { fatalError("Unexpected call") }
    func updateGroupSpace(groupSpaceId: Int64, request: UpdateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { fatalError("Unexpected call") }
    func deleteGroupSpace(groupSpaceId: Int64) async throws { fatalError("Unexpected call") }
    func fetchMembers(groupSpaceId: Int64) async throws -> GroupMemberListResponseDTO { fatalError("Unexpected call") }
    func transferOwnership(groupSpaceId: Int64, request: TransferGroupOwnerRequestDTO) async throws -> TransferGroupOwnerResponseDTO { fatalError("Unexpected call") }
    func leaveGroupSpace(groupSpaceId: Int64) async throws { fatalError("Unexpected call") }
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws { fatalError("Unexpected call") }
}
