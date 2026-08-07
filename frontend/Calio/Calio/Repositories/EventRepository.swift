//
//  EventRepository.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

protocol EventRepository {
    func fetchEvents(from startDate: Date, to endDate: Date) async throws -> [EventResponseDTO]
    func createEvent(_ request: CreateEventRequestDTO) async throws -> EventResponseDTO
    func createRecurrenceEvent(_ request: CreateRecurrenceEventRequestDTO) async throws -> RecurrenceEventResponseDTO
    func fetchRecurrenceEvent(recurrenceId: Int64) async throws -> RecurrenceEventResponseDTO
    func updateEvent(eventId: Int64, request: UpdateEventRequestDTO) async throws -> EventResponseDTO
    func updateRecurrenceEvent(
        recurrenceId: Int64,
        request: UpdateRecurrenceEventRequestDTO
    ) async throws -> RecurrenceEventResponseDTO
    func updateRecurrenceOccurrence(
        recurrenceId: Int64,
        request: UpdateRecurrenceOccurrenceRequestDTO
    ) async throws -> EventResponseDTO
    func deleteEvent(eventId: Int64) async throws
    func deleteRecurrenceEvent(recurrenceId: Int64) async throws
    func deleteRecurrenceOccurrence(recurrenceId: Int64, originStartAt: Date) async throws
}
