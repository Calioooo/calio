import Foundation

struct VoteDay: Hashable, Comparable, Identifiable {
  let year: Int
  let month: Int
  let day: Int

  init(year: Int, month: Int, day: Int) {
    self.year = year
    self.month = month
    self.day = day
  }

  init?(apiDateString: String) {
    let components = apiDateString.split(separator: "-", omittingEmptySubsequences: false)
    guard components.count == 3,
      components[0].count == 4,
      components[1].count == 2,
      components[2].count == 2,
      let year = Int(components[0]),
      let month = Int(components[1]),
      let day = Int(components[2]),
      Self.isValid(year: year, month: month, day: day)
    else {
      return nil
    }

    self.init(year: year, month: month, day: day)
  }

  var id: String {
    apiDateString
  }

  var apiDateString: String {
    String(format: "%04d-%02d-%02d", year, month, day)
  }

  static func < (lhs: VoteDay, rhs: VoteDay) -> Bool {
    if lhs.year != rhs.year {
      return lhs.year < rhs.year
    }
    if lhs.month != rhs.month {
      return lhs.month < rhs.month
    }
    return lhs.day < rhs.day
  }

  private static func isValid(year: Int, month: Int, day: Int) -> Bool {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0)!
    let components = DateComponents(year: year, month: month, day: day)
    guard let date = calendar.date(from: components) else {
      return false
    }
    let validated = calendar.dateComponents([.year, .month, .day], from: date)
    return validated.year == year && validated.month == month && validated.day == day
  }
}

struct VoteRoom: Identifiable, Equatable {
  let publicId: UUID
  let name: String
  let candidateStartDay: VoteDay
  let candidateEndDay: VoteDay

  var id: UUID {
    publicId
  }
}

struct VoteDateResult: Identifiable, Equatable {
  let day: VoteDay
  let unavailableCount: Int
  let unavailableNicknames: [String]

  var id: VoteDay {
    day
  }
}

struct VoteResult: Equatable {
  let room: VoteRoom
  let dateResults: [VoteDateResult]
  let submittedNicknames: [String]
}

enum VoteParticipantStatus: String, Equatable {
  case registered
  case submitted
}

struct VoteParticipant: Equatable {
  let nickname: String
  let status: VoteParticipantStatus
}

struct VoteParticipantSelection: Equatable {
  let participant: VoteParticipant
  let unavailableDays: [VoteDay]
}

struct VoteSubmission: Equatable {
  let participant: VoteParticipant
  let unavailableDays: [VoteDay]
}
