import Foundation

struct TagService {
    private let repository: TagRepository

    init(repository: TagRepository = URLSessionTagRepository()) {
        self.repository = repository
    }

    func fetchTags() async throws -> [CalendarTag] {
        do {
            return try await repository.fetchTags().map(mapToCalendarTag(_:))
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch {
            throw TagServiceError.unexpected
        }
    }

    func createCustomTag(_ input: CustomTagInput) async throws -> CalendarTag {
        let request = CustomTagRequestDTO(
            title: input.title,
            colorCode: input.colorCode
        )

        do {
            let dto = try await repository.createCustomTag(request)
            return mapToCalendarTag(dto)
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch {
            throw TagServiceError.unexpected
        }
    }

    func updateCustomTag(tagId: Int64, input: CustomTagInput) async throws -> CalendarTag {
        let request = CustomTagRequestDTO(
            title: input.title,
            colorCode: input.colorCode
        )

        do {
            let dto = try await repository.updateCustomTag(tagId: tagId, request: request)
            return mapToCalendarTag(dto)
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch {
            throw TagServiceError.unexpected
        }
    }

    func deleteCustomTag(tagId: Int64) async throws {
        do {
            try await repository.deleteCustomTag(tagId: tagId)
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch {
            throw TagServiceError.unexpected
        }
    }

    private func mapToCalendarTag(_ dto: TagResponseDTO) -> CalendarTag {
        CalendarTag(
            id: dto.id,
            title: dto.title,
            colorCode: dto.colorCode,
            tagType: dto.tagType
        )
    }

    private func mapToServiceError(_ error: APIError) -> TagServiceError {
        switch error {
        case .network:
            return .network
        case .decoding:
            return .decoding
        case .backend(_, let problem) where problem?.errorCode == "VALIDATION_FAILED":
            return .validationFailed
        case .invalidRequest, .invalidResponse, .backend, .encoding, .unexpected:
            return .unexpected
        }
    }
}

enum TagServiceError: Error, Equatable {
    case validationFailed
    case network
    case decoding
    case unexpected
}
