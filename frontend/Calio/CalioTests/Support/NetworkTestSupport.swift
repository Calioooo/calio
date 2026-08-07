import Testing
import Foundation
import SwiftUI
@testable import Calio

func requestBodyData(from request: URLRequest) -> Data? {
    if let httpBody = request.httpBody {
        return httpBody
    }

    guard let stream = request.httpBodyStream else {
        return nil
    }

    stream.open()
    defer {
        stream.close()
    }

    var data = Data()
    var buffer = [UInt8](repeating: 0, count: 1024)

    while stream.hasBytesAvailable {
        let count = stream.read(&buffer, maxLength: buffer.count)

        guard count > 0 else {
            break
        }

        data.append(buffer, count: count)
    }

    return data
}

final class MockURLProtocol: URLProtocol {
    static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        guard let handler = MockURLProtocol.requestHandler else {
            return
        }

        do {
            let (response, data) = try handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}

struct StaticAuthTokenProvider: AuthTokenProvider {
    let accessToken: String?
}

final class InMemoryAuthTokenStore: AuthTokenStore {
    private var storedAccessToken: String?

    var accessToken: String? {
        storedAccessToken
    }

    func loadAccessToken() throws -> String? {
        storedAccessToken
    }

    func saveAccessToken(_ accessToken: String) throws {
        storedAccessToken = accessToken
    }

    func deleteAccessToken() throws {
        storedAccessToken = nil
    }
}

final class RecordingAuthRepository: AuthRepository {
    private let response: GuestAuthResponseDTO
    private(set) var issueGuestTokenCallCount = 0

    init(response: GuestAuthResponseDTO) {
        self.response = response
    }

    func issueGuestToken() async throws -> GuestAuthResponseDTO {
        issueGuestTokenCallCount += 1
        return response
    }
}
