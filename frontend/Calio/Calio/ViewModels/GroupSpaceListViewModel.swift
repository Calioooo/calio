import Foundation
import Combine

@MainActor final class GroupSpaceListViewModel: ObservableObject {
    @Published private(set) var spaces: [GroupSpace] = []
    @Published private(set) var isLoading = false
    @Published private(set) var didFailLoading = false
    @Published private(set) var failure: GroupSpaceFailure?
    @Published private(set) var failedOperation: GroupSpaceOperation?
    @Published var errorMessage: String?
    private let service: GroupSpaceService
    private var latestLoadRequestID = 0
    init(service: GroupSpaceService = GroupSpaceService()) { self.service = service }
    deinit {}

    func load() async {
        latestLoadRequestID += 1
        let requestID = latestLoadRequestID
        isLoading = true
        do {
            let spaces = try await service.fetchGroupSpaces()
            guard requestID == latestLoadRequestID else { return }
            self.spaces = spaces
            didFailLoading = false
            clearFailure()
            isLoading = false
        } catch is CancellationError {
            guard requestID == latestLoadRequestID else { return }
            isLoading = false
        } catch {
            guard requestID == latestLoadRequestID else { return }
            didFailLoading = spaces.isEmpty
            recordFailure(error, for: .load)
            isLoading = false
        }
    }
    func create(name: String, nickname: String) async -> Bool {
        do {
            let space = try await service.create(name: name, emoji: nil, nickname: nickname)
            spaces.append(space)
            clearFailure()
            return true
        } catch is CancellationError {
            return false
        } catch {
            recordFailure(error, for: .create)
            return false
        }
    }
    func replace(_ updatedSpace: GroupSpace) {
        guard let index = spaces.firstIndex(where: { $0.groupSpaceId == updatedSpace.groupSpaceId }) else { return }
        spaces[index] = updatedSpace
    }
    func remove(groupSpaceId: Int64) {
        spaces.removeAll { $0.groupSpaceId == groupSpaceId }
    }
    func clearError() { clearFailure() }

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
}
