import Foundation

struct URLSessionNationalHolidayRepository: NationalHolidayRepository {
    private let apiClient: APIClient

    init(
        baseURL: URL = CalioAPIConfig.baseURL,
        session: URLSession = .shared,
        jsonDecoder: JSONDecoder = APIJSONCoding.makeDecoder()
    ) {
        self.apiClient = APIClient(
            baseURL: baseURL,
            session: session,
            jsonDecoder: jsonDecoder
        )
    }

    func fetchNationalHolidays(from startDay: DayKey, to endDay: DayKey) async throws -> [NationalHolidayResponseDTO] {
        try await apiClient.send(
            [NationalHolidayResponseDTO].self,
            method: .get,
            pathComponents: ["api", "national-holidays"],
            queryItems: [
                URLQueryItem(name: "from", value: Self.dateString(from: startDay)),
                URLQueryItem(name: "to", value: Self.dateString(from: endDay))
            ],
            authorization: .none
        )
    }

    private static func dateString(from day: DayKey) -> String {
        String(format: "%04d-%02d-%02d", day.year, day.month, day.day)
    }
}
