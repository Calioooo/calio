//
//  StubEventRepository.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

struct StubEventRepository: EventRepository {
    
    private let calendar: Calendar
    
    init(calendar: Calendar = .current) {
        self.calendar = calendar
    }
    
    func fetchEvents(from startDate: Date, to endDate: Date) async throws -> [EventResponseDTO] {
        sampleEvents(around: startDate).filter { event in
            event.startAt >= startDate && event.endAt <= endDate
        }
    }

    func createEvent(_ request: CreateEventRequestDTO) async throws -> EventResponseDTO {
        EventResponseDTO(
            id: Int64(Date().timeIntervalSince1970),
            title: request.title,
            description: request.description,
            startAt: request.startAt,
            endAt: request.endAt,
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
            recurrenceFrequency: request.recurrenceFrequency
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
            recurrenceFrequency: .daily
        )
    }

    func updateEvent(eventId: Int64, request: UpdateEventRequestDTO) async throws -> EventResponseDTO {
        EventResponseDTO(
            id: eventId,
            title: request.title,
            description: request.description,
            startAt: request.startAt,
            endAt: request.endAt,
            createdAt: Date(),
            updatedAt: Date()
        )
    }

    func updateRecurrenceEvent(
        recurrenceId: Int64,
        request: UpdateRecurrenceEventRequestDTO
    ) async throws -> RecurrenceEventResponseDTO {
        guard let title = request.title,
              let startAt = request.startAt,
              let endAt = request.endAt,
              let recurrenceFrequency = request.recurrenceFrequency
        else {
            throw EventRepositoryError.invalidResponse
        }

        RecurrenceEventResponseDTO(
            recurrenceId: recurrenceId,
            recurrenceTitle: title,
            recurrenceDescription: request.description,
            recurrenceStartDate: CalendarDateService.utcDateString(from: startAt),
            recurrenceEndDate: CalendarDateService.utcDateString(from: endAt),
            recurrenceStartTime: CalendarDateService.utcTimeString(from: startAt),
            recurrenceEndTime: CalendarDateService.utcTimeString(from: endAt),
            recurrenceFrequency: recurrenceFrequency
        )
    }

    func updateRecurrenceOccurrence(
        recurrenceId: Int64,
        eventId: Int64,
        request: UpdateRecurrenceOccurrenceRequestDTO
    ) async throws -> EventResponseDTO {
        guard let title = request.title,
              let startAt = request.startAt,
              let endAt = request.endAt,
              let isImportant = request.isImportant
        else {
            throw EventRepositoryError.invalidResponse
        }

        EventResponseDTO(
            id: eventId,
            title: title,
            description: request.description,
            startAt: startAt,
            endAt: endAt,
            importantEvent: isImportant,
            recurrenceId: recurrenceId,
            isRecurrenceOccurrence: true,
            createdAt: startAt,
            updatedAt: endAt
        )
    }

    func deleteEvent(eventId: Int64) async throws {}

    func deleteRecurrenceEvent(recurrenceId: Int64) async throws {}

    func deleteRecurrenceOccurrence(recurrenceId: Int64, eventId: Int64) async throws {}
    
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
            createdAt: startAt,
            updatedAt: startAt
        )
    }
}
