import Foundation

struct URLSessionEventRepository: EventRepository {
    private let apiClient: APIClient

    init(
        baseURL: URL = CalioAPIConfig.baseURL,
        session: URLSession = .shared,
        jsonDecoder: JSONDecoder = APIJSONCoding.makeDecoder(),
        jsonEncoder: JSONEncoder = APIJSONCoding.makeEncoder(),
        authTokenProvider: AuthTokenProvider? = KeychainAuthTokenStore.shared
    ) {
        self.apiClient = APIClient(
            baseURL: baseURL,
            session: session,
            jsonDecoder: jsonDecoder,
            jsonEncoder: jsonEncoder,
            authTokenProvider: authTokenProvider
        )
    }

    func fetchEvents(from startDate: Date, to endDate: Date) async throws -> [EventResponseDTO] {
        try await apiClient.send(
            [EventResponseDTO].self,
            method: .get,
            pathComponents: ["api", "events"],
            queryItems: [
                URLQueryItem(name: "from", value: APIJSONCoding.string(from: startDate)),
                URLQueryItem(name: "to", value: APIJSONCoding.string(from: endDate))
            ],
            authorization: .bearer
        )
    }

    func createEvent(_ request: CreateEventRequestDTO) async throws -> EventResponseDTO {
        try await apiClient.send(
            EventResponseDTO.self,
            method: .post,
            pathComponents: ["api", "events"],
            authorization: .bearer,
            body: request
        )
    }

    func createRecurrenceEvent(_ request: CreateRecurrenceEventRequestDTO) async throws -> RecurrenceEventResponseDTO {
        try await apiClient.send(
            RecurrenceEventResponseDTO.self,
            method: .post,
            pathComponents: ["api", "recurrence-events"],
            authorization: .bearer,
            body: request
        )
    }

    func fetchRecurrenceEvent(recurrenceId: Int64) async throws -> RecurrenceEventResponseDTO {
        try await apiClient.send(
            RecurrenceEventResponseDTO.self,
            method: .get,
            pathComponents: ["api", "recurrence-events", String(recurrenceId)],
            authorization: .bearer
        )
    }

    func updateEvent(eventId: Int64, request: UpdateEventRequestDTO) async throws -> EventResponseDTO {
        try await apiClient.send(
            EventResponseDTO.self,
            method: .put,
            pathComponents: ["api", "events", String(eventId)],
            authorization: .bearer,
            body: request
        )
    }

    func updateRecurrenceEvent(
        recurrenceId: Int64,
        request: UpdateRecurrenceEventRequestDTO
    ) async throws -> RecurrenceEventResponseDTO {
        try await apiClient.send(
            RecurrenceEventResponseDTO.self,
            method: .put,
            pathComponents: ["api", "recurrence-events", String(recurrenceId)],
            authorization: .bearer,
            body: request
        )
    }

    func updateRecurrenceOccurrence(
        recurrenceId: Int64,
        request: UpdateRecurrenceOccurrenceRequestDTO
    ) async throws -> EventResponseDTO {
        try await apiClient.send(
            EventResponseDTO.self,
            method: .patch,
            pathComponents: ["api", "recurrence-events", String(recurrenceId), "occurrences"],
            authorization: .bearer,
            body: request
        )
    }

    func deleteEvent(eventId: Int64) async throws {
        try await apiClient.sendWithoutResponse(
            method: .delete,
            pathComponents: ["api", "events", String(eventId)],
            authorization: .bearer
        )
    }

    func deleteRecurrenceEvent(recurrenceId: Int64) async throws {
        try await apiClient.sendWithoutResponse(
            method: .delete,
            pathComponents: ["api", "recurrence-events", String(recurrenceId)],
            authorization: .bearer
        )
    }

    func deleteRecurrenceOccurrence(recurrenceId: Int64, originStartAt: Date) async throws {
        try await apiClient.sendWithoutResponse(
            method: .delete,
            pathComponents: ["api", "recurrence-events", String(recurrenceId), "occurrences"],
            queryItems: [
                URLQueryItem(name: "originStartAt", value: APIJSONCoding.string(from: originStartAt))
            ],
            authorization: .bearer
        )
    }
}
