import Foundation

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
