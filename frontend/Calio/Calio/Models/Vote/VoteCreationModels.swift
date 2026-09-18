import Foundation

struct VoteCandidatePeriod: Equatable {
  let startDay: VoteDay
  let lastSelectableEndDay: VoteDay

  func contains(_ day: VoteDay) -> Bool {
    startDay <= day && day <= lastSelectableEndDay
  }
}

struct VoteMonth: Equatable {
  let year: Int
  let month: Int

  init(day: VoteDay) {
    year = day.year
    month = day.month
  }
}

enum VoteCreationState: Equatable {
  case editing
  case creating
  case failed(VoteCreationFailure)
  case created(VoteRoom)

  var isCreating: Bool {
    if case .creating = self {
      return true
    }
    return false
  }
}

enum VoteCreationFailure: Equatable {
  case validation
  case network
  case unexpected

  var message: String {
    switch self {
    case .validation:
      return "투표방 이름과 후보 종료일을 확인해주세요."
    case .network:
      return "네트워크 연결을 확인하고 다시 시도해주세요."
    case .unexpected:
      return "투표방을 만들지 못했습니다. 잠시 후 다시 시도해주세요."
    }
  }
}
