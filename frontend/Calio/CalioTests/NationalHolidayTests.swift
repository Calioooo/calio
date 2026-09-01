import Testing
import Foundation
import SwiftUI
@testable import Calio

@Suite(.serialized)
struct NationalHolidayTests {

    @Test func nationalHolidayResponseDTOKeepsHolidayDateStringAndMapsToDayKey() async throws {
        let calendar = fixedCalendar
        let responseJSON = """
        {
          "nationalHolidayId": 1,
          "holidayDate": "2026-06-06",
          "holidayTitle": "현충일"
        }
        """.data(using: .utf8)!
        let dto = try JSONDecoder().decode(NationalHolidayResponseDTO.self, from: responseJSON)
        let service = NationalHolidayService(
            repository: RecordingNationalHolidayRepository(fetchResponse: [dto]),
            calendar: calendar
        )

        let holidays = try await service.fetchNationalHolidays(for: YearMonthKey(year: 2026, month: 6))
        let holiday = try #require(holidays.first)

        #expect(dto.holidayDate == "2026-06-06")
        #expect(holiday.id == 1)
        #expect(holiday.day == DayKey(year: 2026, month: 6, day: 6))
        #expect(holiday.title == "현충일")
    }

    @Test func nationalHolidayServiceRejectsInvalidHolidayDate() async throws {
        let service = NationalHolidayService(
            repository: RecordingNationalHolidayRepository(
                fetchResponse: [
                    NationalHolidayResponseDTO(
                        nationalHolidayId: 1,
                        holidayDate: "2026-02-30",
                        holidayTitle: "잘못된 날짜"
                    )
                ]
            ),
            calendar: fixedCalendar
        )
        var thrownError: NationalHolidayServiceError?

        do {
            _ = try await service.fetchNationalHolidays(for: YearMonthKey(year: 2026, month: 2))
        } catch let error as NationalHolidayServiceError {
            thrownError = error
        }

        #expect(thrownError == .invalidHolidayDate)
    }

    @Test func nationalHolidayDisplayRangeUsesLocalCalendarStartOfDay() async throws {
        var kstCalendar = Calendar(identifier: .gregorian)
        kstCalendar.timeZone = TimeZone(secondsFromGMT: 9 * 3600)!
        let holiday = NationalHoliday(
            id: 1,
            day: DayKey(year: 2026, month: 6, day: 6),
            title: "현충일"
        )

        let startComponents = kstCalendar.dateComponents(
            [.year, .month, .day, .hour, .minute],
            from: holiday.displayStartAt(calendar: kstCalendar)
        )
        let endComponents = kstCalendar.dateComponents(
            [.year, .month, .day, .hour, .minute],
            from: holiday.displayEndAt(calendar: kstCalendar)
        )

        #expect(startComponents.year == 2026)
        #expect(startComponents.month == 6)
        #expect(startComponents.day == 6)
        #expect(startComponents.hour == 0)
        #expect(startComponents.minute == 0)
        #expect(endComponents.year == 2026)
        #expect(endComponents.month == 6)
        #expect(endComponents.day == 7)
        #expect(endComponents.hour == 0)
        #expect(endComponents.minute == 0)
    }
}
