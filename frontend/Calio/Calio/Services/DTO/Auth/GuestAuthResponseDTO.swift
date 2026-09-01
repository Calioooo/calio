import Foundation

struct GuestAuthResponseDTO: Decodable, Equatable {
    let accessToken: String
    let tokenType: String
}
