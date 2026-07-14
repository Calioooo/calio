//
//  ContentView.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import SwiftUI

struct ContentView: View {
    @State private var selectedTab = 0
    @State private var authState: AuthBootstrapState = .loading
    @State private var googleCalendarAuthCode: GoogleCalendarAuthCodeAlert?
    @State private var googleCalendarAuthFailureMessage: String?
    @StateObject private var viewModel: CalendarHomeViewModel
    private let authService: AuthService
    private let googleCalendarAuthorizationService: GoogleCalendarAuthorizationService

    @MainActor
    init(
        authService: AuthService = AuthService(),
        googleCalendarAuthorizationService: GoogleCalendarAuthorizationService = GoogleCalendarAuthorizationService()
    ) {
        _viewModel = StateObject(wrappedValue: CalendarHomeViewModel())
        self.authService = authService
        self.googleCalendarAuthorizationService = googleCalendarAuthorizationService
    }

    @MainActor
    init(
        viewModel: CalendarHomeViewModel,
        authService: AuthService = AuthService(),
        googleCalendarAuthorizationService: GoogleCalendarAuthorizationService = GoogleCalendarAuthorizationService()
    ) {
        _viewModel = StateObject(wrappedValue: viewModel)
        self.authService = authService
        self.googleCalendarAuthorizationService = googleCalendarAuthorizationService
    }
    
    var body: some View {
        Group {
            switch authState {
            case .loading:
                authLoadingView
            case .authenticated:
                authenticatedContent
            case .failed:
                authFailureView
            }
        }
        .task {
            await bootstrapAuthenticationIfNeeded()
        }
        .alert(item: $googleCalendarAuthCode) { authCode in
            Alert(
                title: Text("serverAuthCode"),
                message: Text(authCode.value),
                dismissButton: .default(Text("확인"))
            )
        }
        .alert(
            "Google Calendar 연동 실패",
            isPresented: Binding(
                get: { googleCalendarAuthFailureMessage != nil },
                set: { isPresented in
                    if !isPresented {
                        googleCalendarAuthFailureMessage = nil
                    }
                }
            )
        ) {
            Button("확인", role: .cancel) {
                googleCalendarAuthFailureMessage = nil
            }
        } message: {
            Text(googleCalendarAuthFailureMessage ?? "")
        }
    }

    private var authenticatedContent: some View {
        TabView(selection: $selectedTab) {
            CalendarHomeView(
                viewModel: viewModel,
                onGoogleCalendarConnectTapped: requestGoogleCalendarAuthorization
            )
                .tabItem {
                    Image(systemName: "calendar")
                    Text("Home")
                }
                .tag(0)
            
            CalendarWeekTimelineScreen(
                viewModel: viewModel,
                onGoogleCalendarConnectTapped: requestGoogleCalendarAuthorization
            )
                .tabItem {
                    Image(systemName: "calendar.day.timeline.left")
                    Text("Week")
                }
                .tag(1)
            
            CalendarMonthScheduleScreen(
                viewModel: viewModel,
                onGoogleCalendarConnectTapped: requestGoogleCalendarAuthorization
            )
                .tabItem {
                    Image(systemName: "calendar")
                    Text("Month")
                }
                .tag(2)
        }
    }

    private var authLoadingView: some View {
        VStack(spacing: 12) {
            ProgressView()
            Text("인증 준비 중")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var authFailureView: some View {
        VStack(spacing: 16) {
            Text("서버 연결에 실패했습니다.")
                .font(.headline)
            Button("다시 시도") {
                retryAuthentication()
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    @MainActor
    private func bootstrapAuthenticationIfNeeded() async {
        guard authState == .loading else {
            return
        }

        do {
            _ = try await authService.ensureGuestAuthentication()
            authState = .authenticated
        } catch {
            authState = .failed
        }
    }

    private func retryAuthentication() {
        authState = .loading
        Task {
            await bootstrapAuthenticationIfNeeded()
        }
    }

    private func requestGoogleCalendarAuthorization() {
        Task {
            do {
                let serverAuthCode = try await googleCalendarAuthorizationService.requestServerAuthCode()
                debugPrint("Google Calendar serverAuthCode: \(serverAuthCode)")
                googleCalendarAuthCode = GoogleCalendarAuthCodeAlert(value: serverAuthCode)
            } catch {
                googleCalendarAuthFailureMessage = "Google Calendar 인증 코드를 가져오지 못했습니다."
            }
        }
    }
}

private enum AuthBootstrapState: Equatable {
    case loading
    case authenticated
    case failed
}

private struct GoogleCalendarAuthCodeAlert: Identifiable {
    let id = UUID()
    let value: String
}

#Preview {
    ContentView()
}
