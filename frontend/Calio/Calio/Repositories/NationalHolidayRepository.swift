//
//  NationalHolidayRepository.swift
//  Calio
//
//  Created by Codex on 7/6/26.
//

import Foundation

protocol NationalHolidayRepository {
    func fetchNationalHolidays(from startDay: DayKey, to endDay: DayKey) async throws -> [NationalHolidayResponseDTO]
}

enum NationalHolidayRepositoryError: Error {
    case invalidURL
    case invalidResponse
    case network(URLError)
    case backend(statusCode: Int, response: ErrorResponseDTO?)
    case decoding(Error)
    case unexpected(Error)
}
