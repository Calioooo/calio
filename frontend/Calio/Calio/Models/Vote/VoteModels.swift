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
