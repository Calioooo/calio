//
//  NationalHolidayService.swift
//  Calio
//
//  Created by Codex on 7/6/26.
//

import Foundation

struct NationalHolidayService {
    private let repository: NationalHolidayRepository
    private let calendar: Calendar

    init(
        repository: NationalHolidayRepository = URLSessionNationalHolidayRepository(),
        calendar: Calendar = .current
    ) {
        self.repository = repository
        self.calendar = calendar
    }

    func fetchNationalHolidays(for month: YearMonthKey) async throws -> [NationalHoliday] {
        let range = dayRange(for: month)

        do {
            let response = try await repository.fetchNationalHolidays(
                from: range.from,
                to: range.to
            )
            return try response.map(mapToNationalHoliday(_:))
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch let error as NationalHolidayServiceError {
            throw error
        } catch {
            throw NationalHolidayServiceError.unexpected
        }
    }

    func mapToNationalHoliday(_ dto: NationalHolidayResponseDTO) throws -> NationalHoliday {
        NationalHoliday(
            id: dto.nationalHolidayId,
            day: try dayKey(from: dto.holidayDate),
            title: dto.holidayTitle
        )
    }

    private func dayRange(for month: YearMonthKey) -> (from: DayKey, to: DayKey) {
        var components = DateComponents()
        components.year = month.year
        components.month = month.month
        components.day = 1

        guard let monthStart = calendar.date(from: components),
              let dayRange = calendar.range(of: .day, in: .month, for: monthStart)
        else {
            preconditionFailure("Failed to create holiday range from key: \(month)")
        }

        return (
            from: DayKey(year: month.year, month: month.month, day: 1),
            to: DayKey(year: month.year, month: month.month, day: dayRange.count)
        )
    }

    private func dayKey(from value: String) throws -> DayKey {
        let components = value.split(separator: "-", omittingEmptySubsequences: false)

        guard components.count == 3,
              components[0].count == 4,
              components[1].count == 2,
              components[2].count == 2,
              let year = Int(components[0]),
              let month = Int(components[1]),
              let day = Int(components[2])
        else {
            throw NationalHolidayServiceError.invalidHolidayDate
        }

        let normalizedValue = String(format: "%04d-%02d-%02d", year, month, day)
        guard normalizedValue == value else {
            throw NationalHolidayServiceError.invalidHolidayDate
        }

        var dateComponents = DateComponents()
        dateComponents.calendar = calendar
        dateComponents.year = year
        dateComponents.month = month
        dateComponents.day = day

        guard let date = calendar.date(from: dateComponents) else {
            throw NationalHolidayServiceError.invalidHolidayDate
        }

        let verifiedComponents = calendar.dateComponents([.year, .month, .day], from: date)
        guard verifiedComponents.year == year,
              verifiedComponents.month == month,
              verifiedComponents.day == day
        else {
            throw NationalHolidayServiceError.invalidHolidayDate
        }

        return DayKey(year: year, month: month, day: day)
    }

    private func mapToServiceError(_ error: APIError) -> NationalHolidayServiceError {
        switch error {
        case .network:
            return .network
        case .decoding:
            return .decoding
        case .invalidRequest, .invalidResponse, .backend, .encoding, .unexpected:
            return .unexpected
        }
    }
}

enum NationalHolidayServiceError: Error, Equatable {
    case invalidHolidayDate
    case network
    case decoding
    case unexpected
}
