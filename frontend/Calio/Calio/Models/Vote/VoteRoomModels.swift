import Foundation

struct VoteRoomRoute: Identifiable, Equatable {
  let publicId: UUID
  let room: VoteRoom?

  init(room: VoteRoom) {
    publicId = room.publicId
    self.room = room
  }

  init(publicId: UUID) {
    self.publicId = publicId
    room = nil
  }

  var id: UUID { publicId }

  static func from(url: URL) -> VoteRoomRoute? {
    guard url.host == CalioAPIConfig.baseURL.host else { return nil }
    let components = url.pathComponents.filter { $0 != "/" }
    guard components.count == 2, components[0] == "vote-rooms", let publicId = UUID(uuidString: components[1]) else {
      return nil
    }
    return VoteRoomRoute(publicId: publicId)
  }
}

enum VoteRoomLoadState: Equatable {
  case loading
  case loaded
  case failed(VoteRoomFailure)
  case unavailable
}

enum VoteParticipantFlow: Equatable {
  case result
  case existingParticipant
  case newParticipant
  case editing
}

enum VoteRoomFailure: Equatable {
  case credentialInvalid
  case nicknameConflict
  case validation
  case network
  case unexpected

  var message: String {
    switch self {
    case .credentialInvalid:
      return "닉네임과 비밀번호를 다시 확인해주세요."
    case .nicknameConflict:
      return "이미 사용 중인 닉네임입니다. 기존 참여자로 참여해주세요."
    case .validation:
      return "입력한 내용을 확인해주세요."
    case .network:
      return "네트워크 연결을 확인하고 다시 시도해주세요."
    case .unexpected:
      return "요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요."
    }
  }
}
