//
//  CalioUITests.swift
//  CalioUITests
//
//  Created by 김준하 on 6/6/26.
//

import XCTest

final class CalioUITests: XCTestCase {

  override func setUpWithError() throws {
    // Put setup code here. This method is called before the invocation of each
    // test method in the class.

    // In UI tests it is usually best to stop immediately when a failure occurs.
    continueAfterFailure = false

    // In UI tests it’s important to set the initial state, such as interface
    // orientation, required for your tests before they run. The setUp method is
    // a good place to do this.
  }

  @MainActor
  func testVoteEnabledHeaderKeepsTodayAndScalableActionsAtAccessibilitySize() throws {
    let app = XCUIApplication()
    app.launchArguments.append("--ui-testing-calendar-top-bar")
    app.launch()

    let actionIdentifiers = [
      "calendar_navigation_today",
      "calendar_navigation_google_connect",
      "calendar_navigation_add_event",
      "calendar_navigation_create_vote",
      "calendar_navigation_my_votes",
    ]

    for identifier in actionIdentifiers {
      let button = app.buttons[identifier]
      XCTAssertTrue(button.waitForExistence(timeout: 3), "\(identifier) 버튼이 표시되어야 합니다.")
      XCTAssertGreaterThanOrEqual(
        button.frame.height,
        44,
        "\(identifier) 버튼은 최소 44pt 터치 영역을 제공해야 합니다."
      )
    }
  }

}
