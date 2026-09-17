import Testing
import Foundation
import SwiftUI
@testable import Calio

@Suite(.serialized)
struct EventCreationTests {

    @Test func eventCreationDisablesCancelOnlyWhileSaving() async throws {
        #expect(CalendarEventCreationView.isCancelDisabled(isSaving: true))
        #expect(!CalendarEventCreationView.isCancelDisabled(isSaving: false))
    }

    @Test func eventCreationDefaultTimesUseReferenceDayMorningRange() async throws {
        let calendar = fixedCalendar
        let referenceDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 10, hour: 15)))
        let referenceDay = DayKey(date: referenceDate, calendar: calendar)

        let range = CalendarEventCreationView.defaultTimeRange(referenceDay: referenceDay, calendar: calendar)
        let startComponents = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: range.startAt)
        let endComponents = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: range.endAt)

        #expect(startComponents.year == 2026)
        #expect(startComponents.month == 6)
        #expect(startComponents.day == 10)
        #expect(startComponents.hour == 9)
        #expect(startComponents.minute == 0)
        #expect(endComponents.hour == 10)
        #expect(endComponents.minute == 0)
    }

    @Test func eventCreationSaveValidationRequiresTitleAndPositiveTimeRange() async throws {
        let startAt = Date()
        let endAt = startAt.addingTimeInterval(3600)

        #expect(CalendarEventFormRules.canSave(title: "회의", startAt: startAt, endAt: endAt))
        #expect(!CalendarEventFormRules.canSave(title: "   ", startAt: startAt, endAt: endAt))
        #expect(!CalendarEventFormRules.canSave(title: "회의", startAt: startAt, endAt: startAt))
        #expect(!CalendarEventFormRules.canSave(title: "회의", startAt: startAt, endAt: startAt.addingTimeInterval(-1)))
    }

    @Test func eventCreationSaveValidationChecksRecurrenceEndDateWithUTCDate() async throws {
        var kstCalendar = Calendar(identifier: .gregorian)
        kstCalendar.timeZone = TimeZone(secondsFromGMT: 9 * 3600)!
        let startAt = try #require(kstCalendar.date(from: DateComponents(year: 2026, month: 8, day: 1, hour: 9)))
        let endAt = try #require(kstCalendar.date(from: DateComponents(year: 2026, month: 8, day: 1, hour: 10)))
        let sameUTCDate = startAt
        let previousUTCDate = try #require(kstCalendar.date(from: DateComponents(year: 2026, month: 7, day: 31, hour: 8)))

        #expect(CalendarEventFormRules.canSave(
            title: "반복 회의",
            startAt: startAt,
            endAt: endAt,
            isRecurrenceEnabled: true,
            recurrenceStartDate: startAt,
            recurrenceEndDate: nil,
            recurrenceStartTime: startAt,
            recurrenceEndTime: endAt
        ))
        #expect(CalendarEventFormRules.canSave(
            title: "반복 회의",
            startAt: startAt,
            endAt: endAt,
            isRecurrenceEnabled: true,
            recurrenceStartDate: startAt,
            recurrenceEndDate: sameUTCDate,
            recurrenceStartTime: startAt,
            recurrenceEndTime: endAt
        ))
        #expect(!CalendarEventFormRules.canSave(
            title: "반복 회의",
            startAt: startAt,
            endAt: endAt,
            isRecurrenceEnabled: true,
            recurrenceStartDate: startAt,
            recurrenceEndDate: previousUTCDate,
            recurrenceStartTime: startAt,
            recurrenceEndTime: endAt
        ))
        #expect(!CalendarEventFormRules.canSave(
            title: "반복 회의",
            startAt: startAt,
            endAt: endAt,
            isRecurrenceEnabled: true,
            recurrenceStartDate: startAt,
            recurrenceEndDate: sameUTCDate,
            recurrenceStartTime: startAt,
            recurrenceEndTime: startAt
        ))
    }

    @Test func localEventTextParserEvalCasesMatchExpectedDrafts() async throws {
        let calendar = fixedCalendar
        let referenceDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 14, hour: 9)))
        let parser = LocalEventTextParser(calendar: calendar)
        let cases: [LocalEventTextParserEvalCase] = [
            LocalEventTextParserEvalCase(
                text: "내일 오후 3시 회의",
                title: "회의",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 15, hour: 15))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 15, hour: 16)))
            ),
            LocalEventTextParserEvalCase(
                text: "오늘 14:00-15:30 디자인 리뷰",
                title: "디자인 리뷰",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 14, hour: 14))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 14, hour: 15, minute: 30)))
            ),
            LocalEventTextParserEvalCase(
                text: "모레 오전 10시 병원",
                title: "병원",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 16, hour: 10))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 16, hour: 11)))
            ),
            LocalEventTextParserEvalCase(
                text: "다음주 월요일 7시 스탠드업",
                title: "스탠드업",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 20, hour: 7))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 20, hour: 8)))
            ),
            LocalEventTextParserEvalCase(
                text: "금요일 저녁 7시 약속",
                title: "약속",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 17, hour: 19))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 17, hour: 20)))
            ),
            LocalEventTextParserEvalCase(
                text: "7월 20일 오후 2시 세미나",
                title: "세미나",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 20, hour: 14))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 20, hour: 15)))
            ),
            LocalEventTextParserEvalCase(
                text: "8/3 09:30 면담",
                title: "면담",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 3, hour: 9, minute: 30))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 3, hour: 10, minute: 30)))
            ),
            LocalEventTextParserEvalCase(
                text: "20일 11시 미팅",
                title: "미팅",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 20, hour: 11))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 20, hour: 12)))
            ),
            LocalEventTextParserEvalCase(
                text: "매주 월요일 오전 10시 스탠드업",
                title: "스탠드업",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 20, hour: 10))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 20, hour: 11))),
                recurrenceFrequency: .weekly
            ),
            LocalEventTextParserEvalCase(
                text: "매일 오전 10시 루틴",
                title: "루틴",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 14, hour: 10))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 14, hour: 11))),
                recurrenceFrequency: .daily
            ),
            LocalEventTextParserEvalCase(
                text: "7시 저녁 약속",
                title: "약속",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 14, hour: 19))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 14, hour: 20)))
            ),
            LocalEventTextParserEvalCase(
                text: "매월 20일 오후 4시 정산",
                title: "정산",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 20, hour: 16))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 20, hour: 17))),
                recurrenceFrequency: .monthly
            ),
            LocalEventTextParserEvalCase(
                text: "이번주 목요일 오후 6시 회식",
                title: "회식",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 16, hour: 18))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 16, hour: 19)))
            ),
            LocalEventTextParserEvalCase(
                text: "내일 오후 3시부터 4시까지 면접",
                title: "면접",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 15, hour: 15))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 15, hour: 16)))
            ),
            LocalEventTextParserEvalCase(
                text: "오늘 오전 12시 배포 점검",
                title: "배포 점검",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 14, hour: 0))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 14, hour: 1)))
            ),
            LocalEventTextParserEvalCase(
                text: "오늘 오후 12시 점심",
                title: "점심",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 14, hour: 12))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 14, hour: 13)))
            ),
            LocalEventTextParserEvalCase(
                text: "12월 31일 밤 11시 송년회",
                title: "송년회",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 12, day: 31, hour: 23))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2027, month: 1, day: 1, hour: 0)))
            ),
            LocalEventTextParserEvalCase(
                text: "1/2 오전 9시 신년 계획",
                title: "신년 계획",
                startAt: try #require(calendar.date(from: DateComponents(year: 2027, month: 1, day: 2, hour: 9))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2027, month: 1, day: 2, hour: 10)))
            ),
            LocalEventTextParserEvalCase(
                text: "매년 8/3 9시 생일",
                title: "생일",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 3, hour: 9))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 3, hour: 10))),
                recurrenceFrequency: .yearly
            ),
            LocalEventTextParserEvalCase(
                text: "매주 금요일 14:00~15:00 리뷰",
                title: "리뷰",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 17, hour: 14))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 17, hour: 15))),
                recurrenceFrequency: .weekly
            ),
            LocalEventTextParserEvalCase(
                text: "7/18-7/21 여행",
                title: "여행",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 18))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 22))),
                isAllDay: true
            ),
            LocalEventTextParserEvalCase(
                text: "7/18~7/21 여행",
                title: "여행",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 18))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 22))),
                isAllDay: true
            ),
            LocalEventTextParserEvalCase(
                text: "7월 18일~7월 21일 여행",
                title: "여행",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 18))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 22))),
                isAllDay: true
            ),
            LocalEventTextParserEvalCase(
                text: "7월 18일부터 21일까지 여행",
                title: "여행",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 18))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 22))),
                isAllDay: true
            ),
            LocalEventTextParserEvalCase(
                text: "18~21일 동안 여행",
                title: "여행",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 18))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 22))),
                isAllDay: true
            )
        ]

        var passedCount = 0

        for testCase in cases {
            let result = try #require(parser.parse(testCase.text, referenceDate: referenceDate))
            let didPass = result.title == testCase.title
                && result.startAt == testCase.startAt
                && result.endAt == testCase.endAt
                && result.recurrenceFrequency == testCase.recurrenceFrequency
                && result.isAllDay == testCase.isAllDay

            if didPass {
                passedCount += 1
            }

            #expect(result.title == testCase.title, "title mismatch: \(testCase.text)")
            #expect(result.startAt == testCase.startAt, "startAt mismatch: \(testCase.text)")
            #expect(result.endAt == testCase.endAt, "endAt mismatch: \(testCase.text)")
            #expect(result.recurrenceFrequency == testCase.recurrenceFrequency, "recurrence mismatch: \(testCase.text)")
            #expect(result.isAllDay == testCase.isAllDay, "all-day mismatch: \(testCase.text)")
        }

        #expect(passedCount == cases.count)
    }

    @Test func localEventTextParserDateRangesHandleYearRolloverAndRejectInvalidRanges() async throws {
        let calendar = fixedCalendar
        let referenceDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 14, hour: 9)))
        let parser = LocalEventTextParser(calendar: calendar)
        let yearRollover = try #require(parser.parse("12/30-1/2 여행", referenceDate: referenceDate))

        #expect(yearRollover.title == "여행")
        #expect(yearRollover.startAt == calendar.date(from: DateComponents(year: 2026, month: 12, day: 30)))
        #expect(yearRollover.endAt == calendar.date(from: DateComponents(year: 2027, month: 1, day: 3)))
        #expect(yearRollover.isAllDay)
        #expect(parser.parse("7/21-7/18 여행", referenceDate: referenceDate) == nil)
        #expect(parser.parse("2/30-3/2 여행", referenceDate: referenceDate) == nil)
    }

    @Test func localEventTextParserTreatsDateWithoutTimeAsAllDay() async throws {
        let calendar = fixedCalendar
        let referenceDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 14, hour: 9)))
        let parser = LocalEventTextParser(calendar: calendar)

        let relativeDate = try #require(parser.parse("내일 팀 워크숍", referenceDate: referenceDate))
        #expect(relativeDate.title == "팀 워크숍")
        #expect(relativeDate.startAt == calendar.date(from: DateComponents(year: 2026, month: 7, day: 15)))
        #expect(relativeDate.endAt == calendar.date(from: DateComponents(year: 2026, month: 7, day: 16)))
        #expect(relativeDate.isAllDay)

        let explicitDate = try #require(parser.parse("7월 18일 여행 준비", referenceDate: referenceDate))
        #expect(explicitDate.startAt == calendar.date(from: DateComponents(year: 2026, month: 7, day: 18)))
        #expect(explicitDate.endAt == calendar.date(from: DateComponents(year: 2026, month: 7, day: 19)))
        #expect(explicitDate.isAllDay)
    }

    @Test func localEventTextParserIgnoresPlainTitleWithoutDateTimeSignal() async throws {
        let parser = LocalEventTextParser(calendar: fixedCalendar)

        #expect(parser.parse("회의") == nil)
    }

    @Test func localEventTextParserRejectsUnsupportedAmbiguousExpressions() async throws {
        let calendar = fixedCalendar
        let referenceDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 14, hour: 9)))
        let parser = LocalEventTextParser(calendar: calendar)
        let unsupportedTexts = [
            "격주 화요일 오전 10시 스탠드업",
            "평일마다 오전 9시 운동",
            "주말 오후 2시 약속",
            "매월 둘째 화요일 오후 3시 회의",
            "매월 말일 오후 4시 정산"
        ]

        for text in unsupportedTexts {
            #expect(parser.parse(text, referenceDate: referenceDate) == nil, "unsupported expression should not be parsed: \(text)")
        }
    }

    @Test func quickEventCreationDraftUsesParsedValuesForRecurringSubmission() async throws {
        let calendar = fixedCalendar
        let referenceDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 14, hour: 9)))
        let referenceDay = DayKey(date: referenceDate, calendar: calendar)
        let parser = LocalEventTextParser(calendar: calendar)
        let parseResult = try #require(
            parser.parse(
                "매주 월요일 오전 10시 스탠드업",
                referenceDate: referenceDate
            )
        )
        let draft = CalendarEventCreationDraft(
            referenceDay: referenceDay,
            calendar: calendar
        ).applying(parseResult, calendar: calendar)

        #expect(draft.eventInput.title == "스탠드업")
        #expect(draft.eventInput.startAt == calendar.date(from: DateComponents(year: 2026, month: 7, day: 20, hour: 10)))
        #expect(draft.eventInput.endAt == calendar.date(from: DateComponents(year: 2026, month: 7, day: 20, hour: 11)))
        #expect(draft.recurrenceInput.isEnabled)
        #expect(draft.recurrenceInput.frequency == .weekly)
        #expect(draft.recurrenceInput.endDate == calendar.date(from: DateComponents(year: 2027, month: 7, day: 20, hour: 10)))
        #expect(draft.canSave)

        guard case .recurring(let input) = draft.submitInput else {
            Issue.record("반복 파싱 결과는 반복 일정 생성 요청이어야 합니다.")
            return
        }

        #expect(input.title == "스탠드업")
        #expect(input.recurrenceStartDate == draft.recurrenceInput.startDate)
        #expect(input.recurrenceEndDate == draft.recurrenceInput.endDate)
        #expect(input.recurrenceStartTime == draft.recurrenceInput.startTime)
        #expect(input.recurrenceEndTime == draft.recurrenceInput.endTime)
        #expect(input.recurrenceFrequency == .weekly)
    }

    @Test func detailedEventCreationPreservesUnparsedQuickInputAsTitle() async throws {
        let calendar = fixedCalendar
        let referenceDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 14, hour: 9)))
        let draft = CalendarEventCreationDraft(
            referenceDay: DayKey(date: referenceDate, calendar: calendar),
            calendar: calendar
        ).replacingTitle(with: "  파싱되지 않은 회의 제목  ")

        #expect(draft.eventInput.title == "파싱되지 않은 회의 제목")
        #expect(!draft.recurrenceInput.isEnabled)
        #expect(draft.canSave)

        guard case .single(let input) = draft.submitInput else {
            Issue.record("일반 제목은 단일 일정 생성 요청이어야 합니다.")
            return
        }

        #expect(input.title == "파싱되지 않은 회의 제목")
        #expect(input.startAt == draft.eventInput.startAt)
        #expect(input.endAt == draft.eventInput.endAt)
    }

    @Test func eventCreationEntryModeUsesDetailForSelectedDateRangeOnly() async throws {
        let startDay = DayKey(year: 2026, month: 7, day: 18)
        let endDay = DayKey(year: 2026, month: 7, day: 21)
        let dateRange = CalendarDateRange(startDay: startDay, endDay: endDay)

        #expect(CalendarEventCreationEntryMode(initialDateRange: nil) == .quick)
        #expect(CalendarEventCreationEntryMode(initialDateRange: dateRange) == .detailed)
    }

    private struct LocalEventTextParserEvalCase {
        let text: String
        let title: String
        let startAt: Date
        let endAt: Date
        let recurrenceFrequency: RecurrenceFrequency?
        let isAllDay: Bool

        init(
            text: String,
            title: String,
            startAt: Date,
            endAt: Date,
            recurrenceFrequency: RecurrenceFrequency? = nil,
            isAllDay: Bool = false
        ) {
            self.text = text
            self.title = title
            self.startAt = startAt
            self.endAt = endAt
            self.recurrenceFrequency = recurrenceFrequency
            self.isAllDay = isAllDay
        }
    }
}
