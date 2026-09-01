import Foundation

struct ProblemDetailDTO: Decodable, Equatable {
    let type: String?
    let title: String
    let status: Int?
    let detail: String?
    let errorCode: String?
}
