import Testing
import Foundation
import SwiftUI
@testable import Calio

@Suite(.serialized)
struct EventPresentationTests {

    @MainActor
    @Test func sharedEventSummaryPopoverForwardsDetailActionForSelectedEvent() async throws {
        let event = makeEvent(id: 91, on: Date())
        var detailEventID: String?
        let popover = CalendarEventSummaryPopoverView(
            event: event,
            onShowDetail: { detailEvent in
                detailEventID = detailEvent.id
            }
        )

        popover.onShowDetail?(event)

        #expect(popover.event.id == event.id)
        #expect(detailEventID == event.id)
    }

    @Test func eventDetailStatusUsesCanonicalEventFieldsWithoutRawRecurrenceID() async throws {
        let baseDate = Date()
        let repeatedEvent = Event(
            id: 11,
            title: "반복 회의",
            description: "",
            startAt: baseDate,
            endAt: baseDate.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5"),
            importantEvent: true,
            recurrenceId: 12345,
            isRecurrenceOccurrence: false
        )
        let occurrenceEvent = Event(
            id: 12,
            title: "반복 발생 일정",
            description: "",
            startAt: baseDate,
            endAt: baseDate.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5"),
            importantEvent: false,
            recurrenceId: nil,
            isRecurrenceOccurrence: true
        )
        let singleEvent = makeEvent(id: 13, on: baseDate)

        #expect(CalendarEventDetailView.importantStatusText(for: repeatedEvent) == "중요 일정")
        #expect(CalendarEventDetailView.recurrenceStatusText(for: repeatedEvent) == "반복 일정")
        #expect(CalendarEventDetailView.recurrenceStatusText(for: occurrenceEvent) == "반복 일정")
        #expect(CalendarEventDetailView.recurrenceStatusText(for: singleEvent) == "반복 없음")
        #expect(!CalendarEventDetailView.recurrenceStatusText(for: repeatedEvent).contains("12345"))
    }

    @Test func eventDisplayTextIncludesDatesOnlyForMultiDayRanges() async throws {
        let calendar = Calendar(identifier: .gregorian)
        let startAt = calendar.date(from: DateComponents(year: 2026, month: 7, day: 5, hour: 9))!
        let sameDayEndAt = calendar.date(from: DateComponents(year: 2026, month: 7, day: 5, hour: 11))!
        let nextDayEndAt = calendar.date(from: DateComponents(year: 2026, month: 7, day: 6, hour: 11))!
        let sameDayText = CalendarEventDisplayText.compactDateTimeRange(
            startAt: startAt,
            endAt: sameDayEndAt
        )
        let multiDayText = CalendarEventDisplayText.compactDateTimeRange(
            startAt: startAt,
            endAt: nextDayEndAt
        )

        #expect(sameDayText == CalendarEventDisplayText.timeRange(startAt: startAt, endAt: sameDayEndAt))
        #expect(multiDayText != CalendarEventDisplayText.timeRange(startAt: startAt, endAt: nextDayEndAt))
        #expect(multiDayText.contains("7월 5일"))
        #expect(multiDayText.contains("7월 6일"))
    }

    @Test func eventDetailActionsSeparateSingleAndRecurringEvents() async throws {
        let baseDate = Date()
        let singleEvent = makeEvent(id: 21, on: baseDate)
        let recurringEvent = Event(
            id: 22,
            title: "반복 회의",
            description: "",
            startAt: baseDate,
            endAt: baseDate.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5"),
            recurrenceId: 100,
            isRecurrenceOccurrence: true
        )
        let recurringEventWithoutRecurrenceID = Event(
            id: 23,
            title: "반복 회의",
            description: "",
            startAt: baseDate,
            endAt: baseDate.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5"),
            recurrenceId: nil,
            isRecurrenceOccurrence: true
        )

        #expect(CalendarEventDetailView.canUpdateSingleEvent(singleEvent))
        #expect(CalendarEventDetailView.canDeleteSingleEvent(singleEvent))
        #expect(!CalendarEventDetailView.canUpdateSingleEvent(recurringEvent))
        #expect(CalendarEventDetailView.canUpdateRecurringEvent(recurringEvent))
        #expect(!CalendarEventDetailView.canDeleteSingleEvent(recurringEvent))
        #expect(CalendarEventDetailView.canDeleteRecurringEvent(recurringEvent))
        #expect(!CalendarEventDetailView.canUpdateRecurringEvent(recurringEventWithoutRecurrenceID))
        #expect(!CalendarEventDetailView.canDeleteRecurringEvent(recurringEventWithoutRecurrenceID))
    }

    @MainActor
    @Test func eventCreationFormReceivesReusableBindingsWithoutOwningSaveAction() async throws {
        let startAt = Date()
        let endAt = startAt.addingTimeInterval(3600)
        let form = CalendarEventFormView(
            eventInput: .constant(
                EventInput(
                    title: "회의",
                    startAt: startAt,
                    endAt: endAt,
                    description: "설명",
                    tag: .sample(colorCode: "#4F46E5")
                )
            ),
            recurrenceInput: .constant(
                RecurrenceInput(
                    isEnabled: false,
                    startDate: startAt,
                    endDate: startAt,
                    startTime: startAt,
                    endTime: endAt,
                    frequency: .daily
                )
            ),
            onRecurrenceEnabled: {}
        )

        #expect(form.title == "회의")
        #expect(form.mode == .create)
        #expect(CalendarEventFormRules.canSave(title: "회의", startAt: startAt, endAt: endAt))
    }

    @MainActor
    @Test func eventEditFormUsesSingleEventModeWithoutRecurrenceFields() async throws {
        let startAt = Date()
        let endAt = startAt.addingTimeInterval(3600)
        let form = CalendarEventFormView(
            eventInput: .constant(
                EventInput(
                    title: "수정할 일정",
                    startAt: startAt,
                    endAt: endAt,
                    description: "설명",
                    tag: .sample(colorCode: "#EF4444")
                )
            ),
            mode: .editSingleEvent,
            onRecurrenceEnabled: {}
        )

        #expect(form.mode == .editSingleEvent)
        #expect(!form.mode.showsRecurrenceFields)
        #expect(form.recurrenceInput == nil)
        #expect(form.title == "수정할 일정")
    }

    @Test func tagManagementRulesSeparateDefaultAndCustomTagsAndPreferEtcFallback() async throws {
        let workTag = CalendarTag(id: 1, title: "업무", colorCode: "#3B82F6", tagType: .defaultTag)
        let etcTag = CalendarTag(id: 2, title: "기타", colorCode: "#64748B", tagType: .defaultTag)
        let customTag = CalendarTag(id: 3, title: "운동", colorCode: "#10B981", tagType: .custom)
        let rules = CalendarTagManagementRules(tags: [workTag, customTag, etcTag])

        #expect(rules.defaultTags == [workTag, etcTag])
        #expect(rules.customTags == [customTag])
        #expect(rules.fallbackTag == etcTag)
    }

    @Test func tagManagementRulesFallbackToFirstTagThenBuiltInFallback() async throws {
        let customTag = CalendarTag(id: 3, title: "운동", colorCode: "#10B981", tagType: .custom)

        #expect(CalendarTagManagementRules(tags: [customTag]).fallbackTag == customTag)
        #expect(CalendarTagManagementRules(tags: []).fallbackTag == .fallback)
    }

    @Test func tagEditInputRulesLimitTrimAndValidateTitle() async throws {
        let rules = CalendarTagEditInputRules(maxTitleLength: 12)
        let longTitle = "123456789012345"
        let validInput = CustomTagInput(title: "  운동  ", colorCode: "#10B981")
        let blankInput = CustomTagInput(title: "   ", colorCode: "#10B981")
        let longInput = CustomTagInput(title: longTitle, colorCode: "#10B981")

        #expect(rules.limitedTitle(longTitle) == "123456789012")
        #expect(rules.saveInput(from: validInput) == CustomTagInput(title: "운동", colorCode: "#10B981"))
        #expect(rules.canSave(validInput))
        #expect(!rules.canSave(blankInput))
        #expect(!rules.canSave(longInput))
    }

    @MainActor
    @Test func recurrenceEditFormModesSeparateOccurrenceAndSeriesFields() async throws {
        let startAt = Date()
        let endAt = startAt.addingTimeInterval(3600)
        let occurrenceForm = CalendarEventFormView(
            eventInput: .constant(
                EventInput(
                    title: "반복 항목 수정",
                    startAt: startAt,
                    endAt: endAt,
                    description: "설명",
                    tag: .sample(colorCode: "#EF4444")
                )
            ),
            mode: .editRecurrenceOccurrence,
            onRecurrenceEnabled: {}
        )
        let seriesMode = CalendarEventFormMode.editRecurrenceSeries

        #expect(!occurrenceForm.mode.showsRecurrenceFields)
        #expect(occurrenceForm.recurrenceInput == nil)
        #expect(seriesMode.showsRecurrenceFields)
        #expect(!seriesMode.allowsRecurrenceToggle)
        #expect(seriesMode.usesRecurrenceDateAndTime(isRecurrenceEnabled: false))
        #expect(seriesMode.showsRecurrenceFrequency(isRecurrenceEnabled: false))
    }
}
