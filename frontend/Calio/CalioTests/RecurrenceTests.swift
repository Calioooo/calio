import Testing
import Foundation
import SwiftUI
@testable import Calio

@Suite(.serialized)
struct RecurrenceTests {

    @Test func createRecurrenceEventRequestDTOEncodesOnlyBackendContractFields() async throws {
        let request = CreateRecurrenceEventRequestDTO(
            title: "매일 스탠드업",
            description: "팀 동기화",
            allDay: false,
            firstOccurrenceStartAt: Date(timeIntervalSince1970: 1_785_638_400),
            firstOccurrenceEndAt: Date(timeIntervalSince1970: 1_785_640_200),
            timeZone: "Asia/Seoul",
            recurrence: ["RRULE:FREQ=DAILY;UNTIL=20260831T000000Z"],
            tagId: 4
        )

        let data = try APIJSONCoding.makeEncoder().encode(request)
        let object = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])

        #expect(Set(object.keys) == [
            "title", "description", "allDay", "firstOccurrenceStartAt", "firstOccurrenceEndAt", "timeZone", "recurrence", "tagId"
        ])
        #expect(object["recurrence"] as? [String] == ["RRULE:FREQ=DAILY;UNTIL=20260831T000000Z"])
        #expect(object["colorCode"] == nil)
        #expect(object["selectedColorCode"] == nil)
    }

    @Test func updateRecurrenceEventRequestDTOEncodesOnlyBackendContractFields() async throws {
        let request = UpdateRecurrenceEventRequestDTO(
            title: "수정 반복 일정",
            description: "수정 설명",
            allDay: false,
            firstOccurrenceStartAt: Date(timeIntervalSince1970: 1_785_638_400),
            firstOccurrenceEndAt: Date(timeIntervalSince1970: 1_785_642_000),
            timeZone: "Asia/Seoul",
            recurrence: ["RRULE:FREQ=WEEKLY;UNTIL=20260831T000000Z"],
            tagId: nil
        )

        let data = try APIJSONCoding.makeEncoder().encode(request)
        let object = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])

        #expect(Set(object.keys) == ["title", "description", "allDay", "firstOccurrenceStartAt", "firstOccurrenceEndAt", "timeZone", "recurrence"])
        #expect(object["title"] as? String == "수정 반복 일정")
        #expect(object["timeZone"] as? String == "Asia/Seoul")
        #expect(object["recurrence"] as? [String] == ["RRULE:FREQ=WEEKLY;UNTIL=20260831T000000Z"])
        #expect(object["isImportant"] == nil)
        #expect(object["colorCode"] == nil)
    }

    @Test func updateRecurrenceOccurrenceRequestDTOEncodesOnlyBackendContractFields() async throws {
        let originStartAt = Date(timeIntervalSince1970: 1_779_996_400)
        let startAt = Date(timeIntervalSince1970: 1_780_000_000)
        let endAt = startAt.addingTimeInterval(3600)
        let request = UpdateRecurrenceOccurrenceRequestDTO(
            originStartAt: originStartAt,
            title: "수정 제목",
            description: "수정 설명",
            startAt: startAt,
            endAt: endAt,
            allDay: false,
            timeZone: "Asia/Seoul"
        )

        let data = try APIJSONCoding.makeEncoder().encode(request)
        let object = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])

        #expect(Set(object.keys) == ["originStartAt", "title", "description", "startAt", "endAt", "allDay", "timeZone"])
        #expect(object["originStartAt"] as? String == APIJSONCoding.string(from: originStartAt))
        #expect(object["title"] as? String == "수정 제목")
        #expect(object["isImportant"] == nil)
        #expect(object["importantEvent"] == nil)
        #expect(object["recurrenceFrequency"] == nil)
    }

    @Test func recurrenceRuleKeepsOnlySupportedSimpleRulesEditable() async throws {
        let until = Date(timeIntervalSince1970: 1_788_480_000)
        let line = RecurrenceRule.make(frequency: .weekly, until: until, allDay: false)

        #expect(RecurrenceRule.editableRule(from: [line], allDay: false)?.frequency == .weekly)
        #expect(RecurrenceRule.editableRule(from: ["RRULE:FREQ=MONTHLY"], allDay: false) == EditableRecurrenceRule(frequency: .monthly, until: nil))
        #expect(RecurrenceRule.editableRule(from: ["RRULE:FREQ=WEEKLY;COUNT=4"], allDay: false) == nil)
        #expect(RecurrenceRule.editableRule(from: ["RRULE:FREQ=WEEKLY;UNTIL=20260831T000000Z", "EXDATE:20260817T000000Z"], allDay: false) == nil)
    }

    @Test func recurrenceScheduleSerializesNoEndWithoutArtificialUntil() throws {
        let utc = TimeZone(secondsFromGMT: 0)!
        let startDate = Date(timeIntervalSince1970: 1_788_480_000)
        let startTime = Date(timeIntervalSince1970: 32_400)
        let endTime = Date(timeIntervalSince1970: 36_000)

        let schedule = try RecurrenceScheduleBuilder.make(
            startDate: startDate,
            endDate: nil,
            startTime: startTime,
            endTime: endTime,
            frequency: .weekly,
            allDay: false,
            timeZone: utc,
            formTimeZone: utc
        )

        #expect(schedule.recurrence == ["RRULE:FREQ=WEEKLY"])
    }

    @Test func eventServiceCreatesUnboundedSeriesWithFrequencyOnlyRule() async throws {
        let startDate = Date(timeIntervalSince1970: 1_788_480_000)
        let startTime = Date(timeIntervalSince1970: 32_400)
        let endTime = Date(timeIntervalSince1970: 36_000)
        let repository = RecordingEventRepository()
        let service = EventService(repository: repository, deviceTimeZone: TimeZone(secondsFromGMT: 0)!)

        try await service.createRecurrenceEvent(
            RecurrenceEventCreateInput(
                title: "종료 없는 주간 점검",
                description: "",
                recurrenceStartDate: startDate,
                recurrenceEndDate: nil,
                recurrenceStartTime: startTime,
                recurrenceEndTime: endTime,
                recurrenceFrequency: .weekly
            )
        )

        #expect(repository.recurrenceCreateRequests.first?.recurrence == ["RRULE:FREQ=WEEKLY"])
    }

    @Test func eventServiceUpdatesUnboundedSeriesWithFrequencyOnlyRule() async throws {
        let startDate = Date(timeIntervalSince1970: 1_788_480_000)
        let startTime = Date(timeIntervalSince1970: 32_400)
        let endTime = Date(timeIntervalSince1970: 36_000)
        let repository = RecordingEventRepository()
        let service = EventService(repository: repository, deviceTimeZone: TimeZone(secondsFromGMT: 0)!)

        _ = try await service.updateRecurrenceEvent(
            recurrenceId: 12,
            input: RecurrenceEventUpdateInput(
                title: "종료 없는 월간 점검",
                description: "",
                recurrenceStartDate: startDate,
                recurrenceEndDate: nil,
                recurrenceStartTime: startTime,
                recurrenceEndTime: endTime,
                recurrenceFrequency: .monthly
            )
        )

        #expect(repository.updateRecurrenceEventRequests.first?.request.recurrence == ["RRULE:FREQ=MONTHLY"])
    }

    @Test func recurrenceInputRestoresOneYearBoundedDefaultAfterNoEndIsDisabled() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try #require(TimeZone(identifier: "UTC"))
        let startDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 1)))
        let expectedEndDate = try #require(calendar.date(from: DateComponents(year: 2027, month: 8, day: 1)))
        var input = RecurrenceInput(
            isEnabled: true,
            startDate: startDate,
            endDate: startDate,
            startTime: startDate,
            endTime: startDate.addingTimeInterval(3600),
            frequency: .weekly
        )

        input.setNoEndDate(true, calendar: calendar)
        #expect(input.endDate == nil)

        input.setNoEndDate(false, calendar: calendar)
        #expect(input.endDate == expectedEndDate)
    }

    @Test func eventServiceLoadsOnlySimpleLocallyOwnedUnboundedSeriesAsEditable() async throws {
        let startAt = Date(timeIntervalSince1970: 1_788_480_000)
        let response = RecurrenceEventResponseDTO(
            recurrenceId: 12,
            title: "종료 없는 월간 점검",
            description: nil,
            allDay: false,
            firstOccurrenceStartAt: startAt,
            firstOccurrenceEndAt: startAt.addingTimeInterval(3600),
            timeZone: "UTC",
            recurrence: ["RRULE:FREQ=MONTHLY"],
            tag: TagResponseDTO(id: 0, title: "기타", colorCode: "#64748B", tagType: .defaultTag),
            createdAt: startAt,
            updatedAt: startAt,
            canUpdateSeries: true
        )
        let repository = RecordingEventRepository(fetchRecurrenceResponse: response)
        let service = EventService(repository: repository)

        let details = try await service.fetchRecurrenceEvent(recurrenceId: 12)

        #expect(details.isRuleEditable)
        #expect(details.canUpdateSeries)
        #expect(details.recurrenceEndDate == nil)
    }

    @Test func eventServiceKeepsComplexOrExternallyManagedSeriesUnavailableForWholeSeriesEditing() async throws {
        let startAt = Date(timeIntervalSince1970: 1_788_480_000)
        let complexResponse = RecurrenceEventResponseDTO(
            recurrenceId: 12,
            title: "복잡한 반복",
            description: nil,
            allDay: false,
            firstOccurrenceStartAt: startAt,
            firstOccurrenceEndAt: startAt.addingTimeInterval(3600),
            timeZone: "UTC",
            recurrence: ["RRULE:FREQ=WEEKLY;BYDAY=MO"],
            tag: TagResponseDTO(id: 0, title: "기타", colorCode: "#64748B", tagType: .defaultTag),
            createdAt: startAt,
            updatedAt: startAt,
            canUpdateSeries: true
        )
        let externallyManagedResponse = RecurrenceEventResponseDTO(
            recurrenceId: 13,
            title: "외부 반복",
            description: nil,
            allDay: false,
            firstOccurrenceStartAt: startAt,
            firstOccurrenceEndAt: startAt.addingTimeInterval(3600),
            timeZone: "UTC",
            recurrence: ["RRULE:FREQ=WEEKLY"],
            tag: TagResponseDTO(id: 0, title: "기타", colorCode: "#64748B", tagType: .defaultTag),
            createdAt: startAt,
            updatedAt: startAt,
            canUpdateSeries: false
        )

        let complexDetails = try await EventService(
            repository: RecordingEventRepository(fetchRecurrenceResponse: complexResponse)
        ).fetchRecurrenceEvent(recurrenceId: 12)
        let externallyManagedDetails = try await EventService(
            repository: RecordingEventRepository(fetchRecurrenceResponse: externallyManagedResponse)
        ).fetchRecurrenceEvent(recurrenceId: 13)

        #expect(!complexDetails.isRuleEditable)
        #expect(externallyManagedDetails.isRuleEditable)
        #expect(!externallyManagedDetails.canUpdateSeries)
    }

    @Test func recurrenceScheduleUsesSeriesZoneForDstAndInclusiveUntil() throws {
        let utc = TimeZone(secondsFromGMT: 0)!
        let newYork = try #require(TimeZone(identifier: "America/New_York"))
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = utc
        let startDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 3, day: 1)))
        let endDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 3, day: 15)))
        let startTime = try #require(calendar.date(from: DateComponents(year: 2001, month: 1, day: 1, hour: 9)))
        let endTime = try #require(calendar.date(from: DateComponents(year: 2001, month: 1, day: 1, hour: 10)))

        let schedule = try RecurrenceScheduleBuilder.make(
            startDate: startDate,
            endDate: endDate,
            startTime: startTime,
            endTime: endTime,
            frequency: .weekly,
            allDay: false,
            timeZone: newYork,
            formTimeZone: utc
        )

        #expect(APIJSONCoding.string(from: schedule.firstOccurrenceStartAt) == "2026-03-01T14:00:00Z")
        #expect(schedule.recurrence == ["RRULE:FREQ=WEEKLY;UNTIL=20260315T130000Z"])
    }
    @Test func eventServiceCreatesAllDayRecurrenceWithClientOwnedTimeConvention() async throws {
        let calendar = Calendar.current
        let startDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 1)))
        let endDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 31)))
        let repository = RecordingEventRepository()
        let service = EventService(repository: repository)

        try await service.createRecurrenceEvent(
            RecurrenceEventCreateInput(
                title: "매일 휴가",
                description: "",
                recurrenceStartDate: startDate,
                recurrenceEndDate: endDate,
                recurrenceStartTime: startDate,
                recurrenceEndTime: endDate,
                recurrenceFrequency: .daily,
                isAllDay: true
            )
        )
        let request = try #require(repository.recurrenceCreateRequests.first)

        #expect(request.recurrenceStartDate == "2026-08-01")
        #expect(request.recurrenceEndDate == "2026-08-31")
        #expect(request.recurrenceStartTime == "00:00:00")
        #expect(request.recurrenceEndTime == "23:59:59")
    }

    @Test func eventServiceUsesCanonicalAllDayFlagForRecurrenceOccurrence() async throws {
        let backendStartAt = try CalendarDateService.utcDate(from: "2026-08-01")
        let backendEndAt = try CalendarDateService.utcDate(from: "2026-08-02")
        let repository = RecordingEventRepository(
            fetchResponse: [
                EventResponseDTO(
                    id: nil,
                    title: "휴가",
                    description: nil,
                    startAt: backendStartAt,
                    endAt: backendEndAt,
                    allDay: true,
                    timeZone: nil,
                    recurrenceId: 700,
                    isRecurrenceOccurrence: true,
                    originStartAt: backendStartAt,
                    createdAt: backendStartAt,
                    updatedAt: backendStartAt
                )
            ]
        )
        let service = EventService(repository: repository)

        let event = try #require(
            try await service.fetchEvents(from: backendStartAt, to: backendEndAt).first
        )

        #expect(event.isAllDay)
        #expect(CalendarDateService.localDateString(from: event.startAt) == "2026-08-01")
        #expect(CalendarDateService.localDateString(from: event.endAt) == "2026-08-02")
    }

    @Test func eventServiceRecognizesAllDayRecurrenceRuleWithoutBackendFlag() async throws {
        let repository = RecordingEventRepository(
            fetchRecurrenceResponse: RecurrenceEventResponseDTO(
                recurrenceId: 700,
                recurrenceTitle: "휴가",
                recurrenceDescription: nil,
                recurrenceStartDate: "2026-08-01",
                recurrenceEndDate: "2026-08-31",
                recurrenceStartTime: "00:00:00",
                recurrenceEndTime: "23:59:59",
                recurrenceFrequency: .daily
            )
        )
        let service = EventService(repository: repository)

        let details = try await service.fetchRecurrenceEvent(recurrenceId: 700)

        #expect(details.isAllDay)
        #expect(CalendarDateService.localDateString(from: details.recurrenceStartDate) == "2026-08-01")
        #expect(CalendarDateService.localDateString(from: try #require(details.recurrenceEndDate)) == "2026-08-31")
    }
    @Test func eventServiceCreateRecurrenceEventMapsSeparateUTCDateAndTimeIntoRepositoryRequest() async throws {
        var kstCalendar = Calendar(identifier: .gregorian)
        kstCalendar.timeZone = TimeZone(secondsFromGMT: 9 * 3600)!
        let recurrenceStartDate = try #require(kstCalendar.date(from: DateComponents(year: 2026, month: 8, day: 1, hour: 9)))
        let recurrenceEndDate = try #require(kstCalendar.date(from: DateComponents(year: 2026, month: 8, day: 31, hour: 9)))
        let recurrenceStartTime = try #require(kstCalendar.date(from: DateComponents(year: 2026, month: 9, day: 2, hour: 9)))
        let recurrenceEndTime = try #require(kstCalendar.date(from: DateComponents(year: 2026, month: 9, day: 2, hour: 10, minute: 30)))
        let repository = RecordingEventRepository()
        let service = EventService(repository: repository)

        try await service.createRecurrenceEvent(
            RecurrenceEventCreateInput(
                title: "아침 루틴",
                description: "반복 설명",
                recurrenceStartDate: recurrenceStartDate,
                recurrenceEndDate: recurrenceEndDate,
                recurrenceStartTime: recurrenceStartTime,
                recurrenceEndTime: recurrenceEndTime,
                recurrenceFrequency: .weekly
            )
        )
        let request = try #require(repository.recurrenceCreateRequests.first)

        #expect(request.recurrenceTitle == "아침 루틴")
        #expect(request.recurrenceDescription == "반복 설명")
        #expect(request.recurrenceStartDate == "2026-08-01")
        #expect(request.recurrenceEndDate == "2026-08-31")
        #expect(request.recurrenceStartTime == "00:00:00")
        #expect(request.recurrenceEndTime == "01:30:00")
        #expect(request.recurrenceFrequency == .weekly)
        #expect(repository.createRequests.isEmpty)
    }

    @Test func eventServiceFetchesRecurrenceEventFromCanonicalResponse() async throws {
        let repository = RecordingEventRepository(
            fetchRecurrenceResponse: RecurrenceEventResponseDTO(
                recurrenceId: 700,
                recurrenceTitle: "반복 회의",
                recurrenceDescription: "설명",
                recurrenceStartDate: "2026-08-01",
                recurrenceEndDate: "2026-08-31",
                recurrenceStartTime: "00:00:00",
                recurrenceEndTime: "01:30:00",
                recurrenceFrequency: .weekly
            )
        )
        let service = EventService(repository: repository)

        let details = try await service.fetchRecurrenceEvent(recurrenceId: 700)

        #expect(repository.fetchRecurrenceEventIDs == [700])
        #expect(details.title == "반복 회의")
        #expect(details.description == "설명")
        #expect(CalendarDateService.utcDateString(from: details.recurrenceStartDate) == "2026-08-01")
        #expect(CalendarDateService.utcTimeString(from: details.recurrenceEndTime) == "01:30:00")
        #expect(details.recurrenceFrequency == .weekly)
    }

    @Test func eventServiceUpdatesRecurrenceOccurrenceWithOriginStartAtOnly() async throws {
        let calendar = fixedCalendar
        let originStartAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 1, hour: 8)))
        let startAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 1, hour: 9)))
        let endAt = startAt.addingTimeInterval(3600)
        let repository = RecordingEventRepository()
        let service = EventService(repository: repository)

        _ = try await service.updateRecurrenceOccurrence(
            recurrenceId: 700,
            originStartAt: originStartAt,
            input: RecurrenceOccurrenceUpdateInput(
                startAt: startAt,
                endAt: endAt
            )
        )
        let request = try #require(repository.updateRecurrenceOccurrenceRequests.first)

        #expect(request.recurrenceId == 700)
        #expect(request.request.originStartAt == originStartAt)
        #expect(request.request.startAt == startAt)
        #expect(request.request.endAt == endAt)
    }

    @Test func eventServiceUpdatesAllDayRecurrenceOccurrenceWithCanonicalUTCInstants() async throws {
        let calendar = Calendar.current
        let originStartAt = Date(timeIntervalSince1970: 1_786_752_000)
        let startAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 10)))
        let endAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 11)))
        let repository = RecordingEventRepository()
        let service = EventService(repository: repository)

        _ = try await service.updateRecurrenceOccurrence(
            recurrenceId: 700,
            originStartAt: originStartAt,
            input: RecurrenceOccurrenceUpdateInput(
                startAt: startAt,
                endAt: endAt,
                isAllDay: true
            )
        )
        let request = try #require(repository.updateRecurrenceOccurrenceRequests.first)
        let backendStartAt = try CalendarDateService.utcDate(from: "2026-08-10")
        let backendEndAt = try CalendarDateService.utcDate(from: "2026-08-11")

        #expect(request.request.originStartAt == originStartAt)
        #expect(request.request.startAt == backendStartAt)
        #expect(request.request.endAt == backendEndAt)
    }

    @Test func eventServiceMapsRecurrenceUpdateInvalidTimeRangeLikeExistingInvalidRange() async throws {
        let repository = RecordingEventRepository(
            updateRecurrenceError: APIError.backend(
                statusCode: 400,
                problem: ProblemDetailDTO(
                    type: "about:blank",
                    title: "RECURRENCE_UPDATE_TIME_RANGE_INVALID",
                    status: 400,
                    detail: "invalid",
                    errorCode: "RECURRENCE_UPDATE_TIME_RANGE_INVALID",
                )
            )
        )
        let service = EventService(repository: repository)
        var thrownError: EventServiceError?

        do {
            _ = try await service.updateRecurrenceEvent(
                recurrenceId: 700,
                input: RecurrenceEventUpdateInput(
                    title: "반복 수정",
                    description: "",
                    recurrenceStartDate: Date(timeIntervalSince1970: 0),
                    recurrenceEndDate: Date(timeIntervalSince1970: 0),
                    recurrenceStartTime: Date(timeIntervalSince1970: 0),
                    recurrenceEndTime: Date(timeIntervalSince1970: 3600),
                    recurrenceFrequency: .daily
                )
            )
        } catch let error as EventServiceError {
            thrownError = error
        } catch {
            thrownError = .unexpected
        }

        #expect(thrownError == .invalidTimeRange)
    }
}
