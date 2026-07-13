//
//  CalendarEventCreationSheetModifier.swift
//  Calio
//
//  Created by Codex on 7/8/26.
//

import SwiftUI

extension View {
    func eventCreationSheet(
        isPresented: Binding<Bool>,
        viewModel: CalendarHomeViewModel,
        referenceDay: DayKey,
        initialDateRange: CalendarDateRange? = nil
    ) -> some View {
        modifier(
            CalendarEventCreationSheetModifier(
                viewModel: viewModel,
                isPresented: isPresented,
                referenceDay: referenceDay,
                initialDateRange: initialDateRange
            )
        )
    }
}

private struct CalendarEventCreationSheetModifier: ViewModifier {
    @ObservedObject var viewModel: CalendarHomeViewModel
    @Binding var isPresented: Bool

    let referenceDay: DayKey
    let initialDateRange: CalendarDateRange?

    func body(content: Content) -> some View {
        content
            .sheet(isPresented: $isPresented) {
                CalendarEventCreationView(
                    referenceDay: referenceDay,
                    initialDateRange: initialDateRange,
                    tags: viewModel.tags,
                    isSaving: viewModel.createState.isSaving,
                    isTagMutating: viewModel.tagMutationState.isMutating,
                    failureMessage: viewModel.createState.failureMessage,
                    tagMutationFailureMessage: viewModel.tagMutationState.failureMessage,
                    onSave: { input in
                        await viewModel.createEvent(input)
                    },
                    onResetTagMutation: viewModel.resetTagMutationState,
                    onCreateCustomTag: viewModel.createCustomTag(_:),
                    onUpdateCustomTag: viewModel.updateCustomTag(_:input:),
                    onDeleteCustomTag: viewModel.deleteCustomTag(_:)
                )
            }
    }
}
