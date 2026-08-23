import Testing
import Foundation
import SwiftUI
@testable import Calio

@Suite(.serialized)
struct EventServiceTests {

    @Test func createEventRequestDTOEncodesOnlyBackendContractFields() async throws {
        let startAt = Date(timeIntervalSince1970: 1_780_000_000)
        let endAt = startAt.addingTimeInterval(3600)
        let request = CreateEventRequestDTO(
            title: "저녁 약속",
            description: "식당 예약",
            startAt: startAt,
            endAt: endAt,
            allDay: false,
            timeZone: "Asia/Seoul"
        )

        let data = try APIJSONCoding.makeEncoder().encode(request)
        let object = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])

        #expect(Set(object.keys) == ["title", "description", "startAt", "endAt", "allDay", "timeZone"])
        #expect(object["title"] as? String == "저녁 약속")
        #expect(object["description"] as? String == "식당 예약")
        #expect(object["selectedColorCode"] == nil)
        #expect(object["colorCode"] == nil)
        #expect((object["startAt"] as? String)?.hasSuffix("Z") == true)
        #expect(object["allDay"] as? Bool == false)
        #expect(object["timeZone"] as? String == "Asia/Seoul")
    }

    @Test func createAllDayEventRequestDTOUsesOnlyCanonicalUTCInstants() async throws {
        let startAt = try CalendarDateService.utcDate(from: "2026-07-18")
        let endAt = try CalendarDateService.utcDate(from: "2026-07-22")
        let request = CreateEventRequestDTO(
            title: "여행",
            description: nil,
            startAt: startAt,
            endAt: endAt,
            allDay: true,
            timeZone: nil
        )

        let data = try APIJSONCoding.makeEncoder().encode(request)
        let object = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])

        #expect(Set(object.keys) == ["title", "startAt", "endAt", "allDay"])
        #expect(object["startAt"] as? String == "2026-07-18T00:00:00Z")
        #expect(object["endAt"] as? String == "2026-07-22T00:00:00Z")
        #expect(object["allDay"] as? Bool == true)
        #expect(object["timeZone"] == nil)
        #expect(object["startDate"] == nil)
        #expect(object["endDate"] == nil)
    }

    @Test func updateEventRequestDTOEncodesOnlyBackendContractFields() async throws {
        let startAt = Date(timeIntervalSince1970: 1_780_000_000)
        let endAt = startAt.addingTimeInterval(3600)
        let request = UpdateEventRequestDTO(
            title: "수정 일정",
            description: "수정 메모",
            startAt: startAt,
            endAt: endAt,
            allDay: false,
            timeZone: "America/New_York"
        )

        let data = try APIJSONCoding.makeEncoder().encode(request)
        let object = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])

        #expect(Set(object.keys) == ["title", "description", "startAt", "endAt", "allDay", "timeZone"])
        #expect(object["title"] as? String == "수정 일정")
        #expect(object["description"] as? String == "수정 메모")
        #expect(object["importantEvent"] == nil)
        #expect(object["recurrenceId"] == nil)
        #expect(object["isRecurrenceOccurrence"] == nil)
        #expect(object["colorCode"] == nil)
        #expect(object["allDay"] as? Bool == false)
        #expect(object["timeZone"] as? String == "America/New_York")
    }
    @Test func eventResponseDTODecodingPreservesCanonicalRecurrenceFields() async throws {
        let responseJSON = """
        {
          "id": 12,
          "title": "반복 occurrence",
          "description": "backend canonical fields",
          "startAt": "2026-08-01T00:00:00Z",
          "endAt": "2026-08-01T01:00:00Z",
          "allDay": false,
          "timeZone": "Asia/Seoul",
          "importantEvent": true,
          "recurrenceId": 44,
          "isRecurrenceOccurrence": true,
          "tag": {
            "id": 1,
            "title": "업무",
            "colorCode": "#4F46E5",
            "tagType": "DEFAULT"
          },
          "createdAt": "2026-08-01T00:00:00Z",
          "updatedAt": "2026-08-01T00:00:00Z"
        }
        """.data(using: .utf8)!

        let dto = try APIJSONCoding.makeDecoder().decode(EventResponseDTO.self, from: responseJSON)
        let service = EventService(repository: RecordingEventRepository(fetchResponse: [dto]))
        let events = try await service.fetchEvents(from: dto.startAt, to: dto.endAt)
        let event = try #require(events.first)

        #expect(dto.importantEvent)
        #expect(dto.recurrenceId == 44)
        #expect(dto.isRecurrenceOccurrence)
        #expect(dto.allDay == false)
        #expect(dto.timeZone == "Asia/Seoul")
        #expect(event.importantEvent)
        #expect(event.recurrenceId == 44)
        #expect(event.isRecurrenceOccurrence)
        #expect(event.timeZone == "Asia/Seoul")
    }

    @Test func eventServiceCreateEventMapsRepositoryResponseToAppEvent() async throws {
        let calendar = fixedCalendar
        let startAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 10, hour: 9)))
        let endAt = startAt.addingTimeInterval(3600)
        let repository = RecordingEventRepository(
            createResponse: EventResponseDTO(
                id: 77,
                title: "제품 리뷰",
                description: nil,
                startAt: startAt,
                endAt: endAt,
                allDay: false,
                timeZone: "Asia/Seoul",
                tag: TagResponseDTO(
                    id: 1,
                    title: "업무",
                    colorCode: "#4F46E5",
                    tagType: .defaultTag
                ),
                createdAt: startAt,
                updatedAt: startAt
            )
        )
        let service = EventService(
            repository: repository,
            deviceTimeZone: TimeZone(identifier: "Asia/Seoul")!
        )

        let event = try await service.createEvent(
            EventCreateInput(
                title: "제품 리뷰",
                description: "",
                startAt: startAt,
                endAt: endAt
            )
        )

        #expect(event.backendId == 77)
        #expect(event.title == "제품 리뷰")
        #expect(event.description == "")
        #expect(event.startAt == startAt)
        #expect(event.endAt == endAt)
        #expect(event.timeZone == "Asia/Seoul")
        #expect(event.tag.colorCode == "#4F46E5")
        #expect(repository.createRequests.count == 1)
        #expect(repository.createRequests.first?.allDay == false)
        #expect(repository.createRequests.first?.timeZone == "Asia/Seoul")
    }

    @Test func eventServiceCreatesAllDayEventWithCanonicalLocalDates() async throws {
        let calendar = Calendar.current
        let startAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 18)))
        let endAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 22)))
        let backendStartAt = try CalendarDateService.utcDate(from: "2026-07-18")
        let backendEndAt = try CalendarDateService.utcDate(from: "2026-07-22")
        let repository = RecordingEventRepository(
            createResponse: EventResponseDTO(
                id: 77,
                title: "여행",
                description: nil,
                startAt: backendStartAt,
                endAt: backendEndAt,
                allDay: true,
                timeZone: nil,
                createdAt: backendStartAt,
                updatedAt: backendStartAt
            )
        )
        let service = EventService(repository: repository)

        let event = try await service.createEvent(
            EventCreateInput(
                title: "여행",
                description: "",
                startAt: startAt,
                endAt: endAt,
                isAllDay: true
            )
        )
        let request = try #require(repository.createRequests.first)

        #expect(request.startAt == backendStartAt)
        #expect(request.endAt == backendEndAt)
        #expect(request.allDay)
        #expect(request.timeZone == nil)
        #expect(event.isAllDay)
        #expect(event.startAt == startAt)
        #expect(event.endAt == endAt)
    }

    @Test func allDayDisplayRangePreservesBackendUTCDateAcrossDeviceTimeZones() throws {
        let backendStartAt = try CalendarDateService.utcDate(from: "2026-07-18")
        let backendEndAt = try CalendarDateService.utcDate(from: "2026-07-22")
        var newYorkCalendar = Calendar(identifier: .gregorian)
        newYorkCalendar.timeZone = try #require(TimeZone(identifier: "America/New_York"))

        let range = try CalendarDateService.localAllDayDisplayRange(
            utcStartAt: backendStartAt,
            utcEndAt: backendEndAt,
            calendar: newYorkCalendar
        )

        #expect(CalendarDateService.localDateString(from: range.startAt, calendar: newYorkCalendar) == "2026-07-18")
        #expect(CalendarDateService.localDateString(from: range.endAt, calendar: newYorkCalendar) == "2026-07-22")
    }

    @Test func eventServiceUpdateEventMapsRepositoryResponseAndPreservesCanonicalFields() async throws {
        let calendar = fixedCalendar
        let startAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 10, hour: 9)))
        let endAt = startAt.addingTimeInterval(3600)
        let repository = RecordingEventRepository(
            updateResponse: EventResponseDTO(
                id: 90,
                title: "수정된 반복 항목",
                description: "수정 설명",
                startAt: startAt,
                endAt: endAt,
                allDay: false,
                timeZone: "America/New_York",
                importantEvent: true,
                recurrenceId: 700,
                isRecurrenceOccurrence: true,
                createdAt: startAt,
                updatedAt: endAt
            )
        )
        let service = EventService(
            repository: repository,
            deviceTimeZone: TimeZone(identifier: "America/Los_Angeles")!
        )

        let event = try await service.updateEvent(
            eventId: 90,
            input: EventUpdateInput(
                title: "수정된 반복 항목",
                description: "수정 설명",
                startAt: startAt,
                endAt: endAt,
                timeZone: "America/New_York"
            )
        )

        #expect(event.backendId == 90)
        #expect(event.importantEvent)
        #expect(event.recurrenceId == 700)
        #expect(event.isRecurrenceOccurrence)
        #expect(repository.updateRequests.first?.eventId == 90)
        #expect(repository.updateRequests.first?.request.title == "수정된 반복 항목")
        #expect(repository.updateRequests.first?.request.timeZone == "America/New_York")
    }

    @Test func eventServiceUsesCanonicalAllDayFlagForUTCMidnightTimedEvent() async throws {
        let startAt = try CalendarDateService.utcDate(from: "2026-08-01")
        let endAt = startAt.addingTimeInterval(3600)
        let repository = RecordingEventRepository(
            fetchResponse: [
                EventResponseDTO(
                    id: 5,
                    title: "자정 회의",
                    description: nil,
                    startAt: startAt,
                    endAt: endAt,
                    allDay: false,
                    timeZone: "Asia/Seoul",
                    createdAt: startAt,
                    updatedAt: endAt
                )
            ]
        )

        let event = try #require(
            try await EventService(repository: repository)
                .fetchEvents(from: startAt, to: endAt)
                .first
        )

        #expect(!event.isAllDay)
        #expect(event.startAt == startAt)
        #expect(event.timeZone == "Asia/Seoul")
    }
}
