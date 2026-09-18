import Foundation

protocol VotePersonalScheduleProviding {
  func unavailableDays(in room: VoteRoom) async throws -> Set<VoteDay>
}

struct VotePersonalScheduleService: VotePersonalScheduleProviding {
  private let eventService: EventService
  private let calendar: Calendar

  init(
    eventService: EventService = EventService(),
    calendar: Calendar = .voteKorea
  ) {
    self.eventService = eventService
    self.calendar = calendar
  }

  func unavailableDays(in room: VoteRoom) async throws -> Set<VoteDay> {
    let candidateDays = VoteRoomCalendar.days(in: room, calendar: calendar)
    guard
      let start = VoteRoomCalendar.start(of: room.candidateStartDay, calendar: calendar),
      let end = VoteRoomCalendar.nextStart(of: room.candidateEndDay, calendar: calendar)
    else {
      return []
    }

    let events = try await eventService.fetchEvents(from: start, to: end)
    return Set(candidateDays.filter { day in
      guard
        let dayStart = VoteRoomCalendar.start(of: day, calendar: calendar),
        let dayEnd = VoteRoomCalendar.nextStart(of: day, calendar: calendar)
      else {
        return false
      }
      return events.contains { $0.startAt < dayEnd && $0.endAt > dayStart }
    })
  }
}

enum VoteRoomCalendar {
  static func days(in room: VoteRoom, calendar: Calendar = .voteKorea) -> [VoteDay] {
    guard
      let candidateStart = start(of: room.candidateStartDay, calendar: calendar),
      let candidateEnd = start(of: room.candidateEndDay, calendar: calendar)
    else {
      return []
    }

    var days: [VoteDay] = []
    var date = candidateStart
    while date <= candidateEnd {
      days.append(VoteDay(date: date, calendar: calendar))
      guard let next = calendar.date(byAdding: .day, value: 1, to: date) else { break }
      date = next
    }
    return days
  }

  static func start(of day: VoteDay, calendar: Calendar = .voteKorea) -> Date? {
    calendar.date(from: DateComponents(year: day.year, month: day.month, day: day.day))
  }

  static func nextStart(of day: VoteDay, calendar: Calendar = .voteKorea) -> Date? {
    guard let start = start(of: day, calendar: calendar) else { return nil }
    return calendar.date(byAdding: .day, value: 1, to: start)
  }
}

extension Calendar {
  static var voteKorea: Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "Asia/Seoul")!
    return calendar
  }
}
