//
//  StubEventRepository.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

struct StubEventRepository: EventRepository, TagRepository {
    
    private let calendar: Calendar
    
    init(calendar: Calendar = .current) {
        self.calendar = calendar
    }
    
    func fetchEvents(from startDate: Date, to endDate: Date) async throws -> [EventResponseDTO] {
        sampleEvents(around: startDate).filter { event in
            event.startAt >= startDate && event.endAt <= endDate
        }
    }

    func fetchTags() async throws -> [TagResponseDTO] {
        Self.defaultTags
    }

    func createCustomTag(_ request: CustomTagRequestDTO) async throws -> TagResponseDTO {
        TagResponseDTO(
            id: Int64(Date().timeIntervalSince1970),
            title: request.title,
            colorCode: request.colorCode,
            tagType: .custom
        )
    }

    func updateCustomTag(tagId: Int64, request: CustomTagRequestDTO) async throws -> TagResponseDTO {
        TagResponseDTO(
            id: tagId,
            title: request.title,
            colorCode: request.colorCode,
            tagType: .custom
        )
    }

    func deleteCustomTag(tagId: Int64) async throws {}

    func createEvent(_ request: CreateEventRequestDTO) async throws -> EventResponseDTO {
        let startAt = try request.startAt
            ?? CalendarDateService.localDate(from: request.startDate ?? "")
        let endAt = try request.endAt
            ?? CalendarDateService.localDate(from: request.endDate ?? "")
        return EventResponseDTO(
            id: Int64(Date().timeIntervalSince1970),
            title: request.title,
            description: request.description,
            startAt: startAt,
            endAt: endAt,
            allDay: request.allDay,
            startDate: request.startDate,
            endDate: request.endDate,
            tag: tag(for: request.tagId),
            createdAt: Date(),
            updatedAt: Date()
        )
    }

    func createRecurrenceEvent(_ request: CreateRecurrenceEventRequestDTO) async throws -> RecurrenceEventResponseDTO {
        RecurrenceEventResponseDTO(
            recurrenceId: Int64(Date().timeIntervalSince1970),
            recurrenceTitle: request.recurrenceTitle,
            recurrenceDescription: request.recurrenceDescription,
            recurrenceStartDate: request.recurrenceStartDate,
            recurrenceEndDate: request.recurrenceEndDate,
            recurrenceStartTime: request.recurrenceStartTime,
            recurrenceEndTime: request.recurrenceEndTime,
            recurrenceFrequency: request.recurrenceFrequency,
            allDay: request.allDay,
            tag: tag(for: request.tagId)
        )
    }

    func fetchRecurrenceEvent(recurrenceId: Int64) async throws -> RecurrenceEventResponseDTO {
        RecurrenceEventResponseDTO(
            recurrenceId: recurrenceId,
            recurrenceTitle: "반복 일정",
            recurrenceDescription: "",
            recurrenceStartDate: "2026-01-01",
            recurrenceEndDate: "2026-01-31",
            recurrenceStartTime: "00:00:00",
            recurrenceEndTime: "01:00:00",
            recurrenceFrequency: .daily,
            tag: Self.defaultTags[0]
        )
    }

    func updateEvent(eventId: Int64, request: UpdateEventRequestDTO) async throws -> EventResponseDTO {
        let startAt = try request.startAt
            ?? CalendarDateService.localDate(from: request.startDate ?? "")
        let endAt = try request.endAt
            ?? CalendarDateService.localDate(from: request.endDate ?? "")
        return EventResponseDTO(
            id: eventId,
            title: request.title,
            description: request.description,
            startAt: startAt,
            endAt: endAt,
            allDay: request.allDay,
            startDate: request.startDate,
            endDate: request.endDate,
            tag: tag(for: request.tagId),
            createdAt: Date(),
            updatedAt: Date()
        )
    }

    func updateRecurrenceEvent(
        recurrenceId: Int64,
        request: UpdateRecurrenceEventRequestDTO
    ) async throws -> RecurrenceEventResponseDTO {
        RecurrenceEventResponseDTO(
            recurrenceId: recurrenceId,
            recurrenceTitle: request.title,
            recurrenceDescription: request.description,
            recurrenceStartDate: request.startDate,
            recurrenceEndDate: request.endDate,
            recurrenceStartTime: request.startTime,
            recurrenceEndTime: request.endTime,
            recurrenceFrequency: request.recurrenceFrequency,
            allDay: request.allDay,
            tag: tag(for: request.tagId)
        )
    }

    func updateRecurrenceOccurrence(
        recurrenceId: Int64,
        request: UpdateRecurrenceOccurrenceRequestDTO
    ) async throws -> EventResponseDTO {
        let startAt = try request.startAt
            ?? CalendarDateService.localDate(from: request.startDate ?? "")
        let endAt = try request.endAt
            ?? CalendarDateService.localDate(from: request.endDate ?? "")
        let isAllDay = request.startDate != nil
        return EventResponseDTO(
            id: nil,
            title: "반복 일정",
            description: nil,
            startAt: startAt,
            endAt: endAt,
            allDay: isAllDay,
            startDate: request.startDate,
            endDate: request.endDate,
            recurrenceId: recurrenceId,
            isRecurrenceOccurrence: true,
            originStartAt: request.originStartAt,
            tag: Self.defaultTags[0],
            createdAt: startAt,
            updatedAt: endAt
        )
    }

    func deleteEvent(eventId: Int64) async throws {}

    func deleteRecurrenceEvent(recurrenceId: Int64) async throws {}

    func deleteRecurrenceOccurrence(recurrenceId: Int64, originStartAt: Date) async throws {}
    
    private func sampleEvents(around baseDate: Date) -> [EventResponseDTO] {
        [
            makeEvent(id: 1, dayOffset: 1, hour: 12, minute: 0, durationMinutes: 60, title: "점심 약속", description: "외부 미팅", from: baseDate),
            makeEvent(id: 2, dayOffset: 1, hour: 17, minute: 0, durationMinutes: 60, title: "릴리즈 체크", description: "배포 전 확인", from: baseDate),
            makeEvent(id: 3, dayOffset: 1, hour: 19, minute: 30, durationMinutes: 60, title: "팀 미팅", description: "주간 일정 공유", from: baseDate),
            makeEvent(id: 4, dayOffset: 0, hour: 13, minute: 0, durationMinutes: 90, title: "제품 리뷰", description: "캘린더 화면 구조 확인", from: baseDate),
            makeEvent(id: 5, dayOffset: 1, hour: 10, minute: 0, durationMinutes: 30, title: "1:1 미팅", description: "진행 상황 확인", from: baseDate),
            makeEvent(id: 6, dayOffset: 1, hour: 15, minute: 30, durationMinutes: 60, title: "API 계약 정리", description: "Event 조회 응답 필드 확인", from: baseDate),
            makeEvent(id: 7, dayOffset: 2, hour: 8, minute: 0, durationMinutes: 45, title: "운동", description: "아침 운동", from: baseDate),
            makeEvent(id: 8, dayOffset: 3, hour: 11, minute: 0, durationMinutes: 120, title: "기획 회의", description: "다음 스프린트 범위 논의", from: baseDate),
            makeEvent(id: 9, dayOffset: 5, hour: 18, minute: 30, durationMinutes: 90, title: "저녁 약속", description: "개인 일정", from: baseDate),
            makeEvent(id: 10, dayOffset: 7, hour: 14, minute: 0, durationMinutes: 60, title: "디자인 리뷰", description: "날짜 Strip 상태 확인", from: baseDate),
            makeEvent(id: 11, dayOffset: 1, hour: 20, minute: 0, durationMinutes: 60, title: "점심 약속", description: "외부 미팅", from: baseDate),
            makeEvent(id: 12, dayOffset: 1, hour: 21, minute: 0, durationMinutes: 60, title: "릴리즈 체크", description: "배포 전 확인", from: baseDate),
            makeEvent(id: 13, dayOffset: 1, hour: 22, minute: 30, durationMinutes: 60, title: "팀 미팅", description: "주간 일정 공유", from: baseDate)
        ]
    }
    
    private func makeEvent(
        id: Int64,
        dayOffset: Int,
        hour: Int,
        minute: Int,
        durationMinutes: Int,
        title: String,
        description: String?,
        from baseDate: Date
    ) -> EventResponseDTO {
        let startOfDay = calendar.startOfDay(for: baseDate)
        let eventDay = calendar.date(byAdding: .day, value: dayOffset, to: startOfDay) ?? startOfDay
        
        let startAt = calendar.date(
            bySettingHour: hour,
            minute: minute,
            second: 0,
            of: eventDay
        ) ?? eventDay
        
        let endAt = calendar.date(
            byAdding: .minute,
            value: durationMinutes,
            to: startAt
        ) ?? startAt
        
        return EventResponseDTO(
            id: id,
            title: title,
            description: description,
            startAt: startAt,
            endAt: endAt,
            tag: Self.defaultTags[Int(id - 1) % Self.defaultTags.count],
            createdAt: startAt,
            updatedAt: startAt
        )
    }

    private func tag(for tagId: Int64?) -> TagResponseDTO {
        guard let tagId else {
            return Self.defaultTags.last ?? Self.defaultTags[0]
        }

        return Self.defaultTags.first { $0.id == tagId } ?? Self.defaultTags.last ?? Self.defaultTags[0]
    }

    private static let defaultTags = [
        TagResponseDTO(id: 1, title: "업무", colorCode: "#3B82F6", tagType: .defaultTag),
        TagResponseDTO(id: 2, title: "개인", colorCode: "#A855F7", tagType: .defaultTag),
        TagResponseDTO(id: 3, title: "약속", colorCode: "#F97316", tagType: .defaultTag),
        TagResponseDTO(id: 4, title: "공부", colorCode: "#10B981", tagType: .defaultTag),
        TagResponseDTO(id: 5, title: "기타", colorCode: "#64748B", tagType: .defaultTag)
    ]
}
