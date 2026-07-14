//
//  GoogleCalendarAuthorizationService.swift
//  Calio
//
//  Created by Codex on 7/14/26.
//

import Foundation
import GoogleSignIn
import UIKit

struct GoogleCalendarAuthorizationService {
    private let calendarEventsScope = "https://www.googleapis.com/auth/calendar.events"

    @MainActor
    func requestServerAuthCode() async throws -> String {
        let presentingViewController = try presentingViewController()

        let result = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<GIDSignInResult, Error>) in
            GIDSignIn.sharedInstance.signIn(
                withPresenting: presentingViewController,
                hint: nil,
                additionalScopes: [calendarEventsScope]
            ) { result, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }

                guard let result else {
                    continuation.resume(throwing: GoogleCalendarAuthorizationError.missingSignInResult)
                    return
                }

                continuation.resume(returning: result)
            }
        }

        guard let serverAuthCode = result.serverAuthCode,
              !serverAuthCode.isEmpty else {
            throw GoogleCalendarAuthorizationError.missingServerAuthCode
        }

        return serverAuthCode
    }

    private func presentingViewController() throws -> UIViewController {
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive }),
              let rootViewController = scene.keyWindow?.rootViewController else {
            throw GoogleCalendarAuthorizationError.missingPresentingViewController
        }

        return topMostViewController(from: rootViewController)
    }

    private func topMostViewController(from viewController: UIViewController) -> UIViewController {
        if let presentedViewController = viewController.presentedViewController {
            return topMostViewController(from: presentedViewController)
        }

        if let navigationController = viewController as? UINavigationController,
           let visibleViewController = navigationController.visibleViewController {
            return topMostViewController(from: visibleViewController)
        }

        if let tabBarController = viewController as? UITabBarController,
           let selectedViewController = tabBarController.selectedViewController {
            return topMostViewController(from: selectedViewController)
        }

        return viewController
    }
}

enum GoogleCalendarAuthorizationError: Error, Equatable {
    case missingPresentingViewController
    case missingSignInResult
    case missingServerAuthCode
}
