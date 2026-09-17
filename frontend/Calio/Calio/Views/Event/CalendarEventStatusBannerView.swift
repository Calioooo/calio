//
//  CalendarEventStatusBannerView.swift
//  Calio
//
//  Created by Codex on 6/23/26.
//

import SwiftUI

struct CalendarEventStatusBannerView: View {
    let state: CalendarEventLoadState
    let onRetry: () -> Void

    var body: some View {
        switch state {
        case .idle:
            EmptyView()

        case .loading:
            HStack(spacing: 8) {
                ProgressView()
                    .controlSize(.small)

                Text("일정을 불러오는 중입니다.")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.calioTextSecondary)
            }
            .frame(maxWidth: .infinity, minHeight: 36)
            .background(Color.calioSelection)

        case .failed(let message):
            HStack(spacing: 10) {
                Text(message)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.calioTextSecondary)
                    .lineLimit(2)

                Spacer(minLength: 8)

                Button("다시 시도", action: onRetry)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.calioPrimary)
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, minHeight: 44)
            .background(Color.calioSelection)
        }
    }
}

#Preview {
    VStack(spacing: 0) {
        CalendarEventStatusBannerView(state: .loading, onRetry: {})
        CalendarEventStatusBannerView(
            state: .failed("일정을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."),
            onRetry: {}
        )
    }
}
