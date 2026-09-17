import Foundation

struct NationalHolidayResponseDTO: Decodable, Equatable {
    let nationalHolidayId: Int64
    let holidayDate: String
    let holidayTitle: String
}
