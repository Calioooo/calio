import Foundation

struct TagResponseDTO: Decodable, Equatable {
    let id: Int64
    let title: String
    let colorCode: String
    let tagType: CalendarTagType
}

struct CustomTagRequestDTO: Encodable, Equatable {
    let title: String
    let colorCode: String
}
