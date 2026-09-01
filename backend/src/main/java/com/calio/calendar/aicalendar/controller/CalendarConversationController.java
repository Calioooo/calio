package com.calio.calendar.aicalendar.controller;

import com.calio.calendar.aicalendar.controller.dto.CreateCalendarConversationResponse;
import com.calio.calendar.aicalendar.controller.dto.SendCalendarConversationMessageRequest;
import com.calio.calendar.aicalendar.controller.dto.SendCalendarConversationMessageResponse;
import com.calio.calendar.aicalendar.service.CalendarConversationService;
import com.calio.calendar.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/calendar/conversations")
public class CalendarConversationController {

    private final CalendarConversationService conversationService;

    public CalendarConversationController(CalendarConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<CreateCalendarConversationResponse> createConversation(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        String conversationId = conversationService.createConversation(account.accountId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateCalendarConversationResponse(conversationId));
    }

    @PostMapping("/{conversationId}/messages")
    public SendCalendarConversationMessageResponse sendMessage(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable String conversationId,
            @Valid @RequestBody SendCalendarConversationMessageRequest request
    ) {
        return conversationService.sendMessage(
                account.accountId(),
                conversationId,
                request.message(),
                request.timeZone()
        );
    }
}
