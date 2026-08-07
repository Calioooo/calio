import Foundation
import Testing
@testable import Calio

@Suite(.serialized)
struct TagServiceTests {

    @Test func tagServiceMapsNetworkFailure() async throws {
        let error = await createFailure(
            APIError.network(URLError(.notConnectedToInternet))
        )

        #expect(error == .network)
    }

    @Test func tagServiceMapsDecodingFailure() async throws {
        let error = await createFailure(
            APIError.decoding(
                DecodingError.dataCorrupted(
                    .init(codingPath: [], debugDescription: "Malformed tag response")
                )
            )
        )

        #expect(error == .decoding)
    }

    @Test func tagServiceMapsValidationFailure() async throws {
        let error = await createFailure(
            APIError.backend(
                statusCode: 400,
                problem: ProblemDetailDTO(
                    type: nil,
                    title: "Validation failed",
                    status: 400,
                    detail: nil,
                    errorCode: "VALIDATION_FAILED"
                )
            )
        )

        #expect(error == .validationFailed)
    }

    @Test func tagServiceMapsOtherTechnicalFailuresToUnexpected() async throws {
        let error = await createFailure(APIError.invalidRequest)

        #expect(error == .unexpected)
    }

    private func createFailure(_ repositoryError: Error) async -> TagServiceError? {
        let service = TagService(
            repository: RecordingTagRepository(createError: repositoryError)
        )

        do {
            _ = try await service.createCustomTag(
                CustomTagInput(title: "운동", colorCode: "#10B981")
            )
            return nil
        } catch let error as TagServiceError {
            return error
        } catch {
            return .unexpected
        }
    }
}
