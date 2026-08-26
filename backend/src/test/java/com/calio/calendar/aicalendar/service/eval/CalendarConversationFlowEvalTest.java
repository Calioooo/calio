package com.calio.calendar.aicalendar.service.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.aicalendar.controller.dto.SendCalendarConversationMessageResponse;
import com.calio.calendar.aicalendar.domain.CalendarAssistantBlockType;
import com.calio.calendar.aicalendar.domain.CalendarAssistantRequestClassification;
import com.calio.calendar.aicalendar.domain.CalendarConversationMessageRole;
import com.calio.calendar.aicalendar.domain.CalendarMutationOperation;
import com.calio.calendar.aicalendar.domain.CalendarMutationScope;
import com.calio.calendar.aicalendar.domain.CalendarMutationType;
import com.calio.calendar.aicalendar.repository.CalendarConversationMessageRepository;
import com.calio.calendar.aicalendar.repository.CalendarConversationRepository;
import com.calio.calendar.aicalendar.service.CalendarAssistantRequestClassifier;
import com.calio.calendar.aicalendar.service.CalendarConversationQueryService;
import com.calio.calendar.aicalendar.service.CalendarConversationService;
import com.calio.calendar.aicalendar.service.CalendarMutationService;
import com.calio.calendar.aicalendar.service.dto.CalendarMutationPreview;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarMutationToolRequest;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.EventService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-calendar-conversation-flow-eval;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.ai.model.chat=openai",
        "spring.ai.openai.chat.temperature=0"
})
@Import(CalendarConversationFlowEvalTest.FixedClockConfig.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class CalendarConversationFlowEvalTest {

    @Autowired
    private CalendarConversationService conversationService;

    @Autowired
    private CalendarConversationQueryService conversationQueryService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CalendarConversationRepository conversationRepository;

    @Autowired
    private CalendarConversationMessageRepository messageRepository;

    @Autowired
    private ChatModel chatModel;

    @MockitoBean
    private CalendarAssistantRequestClassifier requestClassifier;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private CalendarMutationService mutationService;

    private final List<SendCalendarConversationMessageResponse> responses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        accountRepository.deleteAll();
        when(requestClassifier.classify(any())).thenReturn(CalendarAssistantRequestClassification.SUPPORTED);
    }

    @AfterEach
    void printConversationResponses(TestInfo testInfo) {
        if (responses.isEmpty()) {
            return;
        }
        System.out.printf(
                """
                AI calendar conversation flow eval responses.
                test: %s
                responses: %s%n
                """,
                testInfo.getDisplayName(),
                responses
        );
    }

    @Test
    @DisplayName("생성에 종료 시각이 없으면 재질문 후 이전 맥락으로 생성 Preview를 만든다")
    void givenCreationWithoutEndTime_whenUserProvidesEndTime_thenCreatesPreviewFromConversationHistory() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        String conversationId = conversationService.createConversation(account.getId());
        CalendarMutationPreview preview = new CalendarMutationPreview(
                CalendarMutationType.CREATE,
                CalendarMutationScope.EVENT,
                null,
                timedEvent("팀 회의", "2026-08-16T05:00:00Z", "2026-08-16T06:00:00Z")
        );
        when(mutationService.preview(eq(account.getId()), any())).thenReturn(preview);

        // when
        SendCalendarConversationMessageResponse first = send(
                account,
                conversationId,
                "내일 오후 2시에 팀 회의 만들어줘"
        );
        SendCalendarConversationMessageResponse second = send(
                account,
                conversationId,
                "오후 3시에 끝나"
        );

        // then
        assertResponseMatchesReference(
                "내일 오후 2시에 팀 회의 만들어줘",
                "The event title and start time are known, but the end time is missing. "
                        + "Ask one concise question for the end time. Do not create a preview yet.",
                first
        );
        assertThat(first.blocks()).noneMatch(block -> block.type() == CalendarAssistantBlockType.MUTATION_PREVIEW);
        assertThat(second.assistantMessage()).isNotBlank();
        assertThat(second.blocks()).extracting(block -> block.type())
                .contains(CalendarAssistantBlockType.MUTATION_PREVIEW);
        CalendarMutationToolRequest request = capturedPreviewRequest();
        assertThat(request.operation()).isEqualTo(CalendarMutationOperation.CREATE_EVENT);
        assertThat(request.title()).isEqualTo("팀 회의");
        assertThat(request.startAt()).isEqualTo(Instant.parse("2026-08-16T05:00:00Z"));
        assertThat(request.endAt()).isEqualTo(Instant.parse("2026-08-16T06:00:00Z"));
        assertConversationHistory(
                account,
                conversationId,
                "내일 오후 2시에 팀 회의 만들어줘",
                first.assistantMessage(),
                "오후 3시에 끝나",
                second.assistantMessage()
        );
        verify(mutationService, never()).apply(any(), any());
    }

    @Test
    @DisplayName("수정 시각이 없으면 재질문 후 이전 맥락으로 수정 Preview를 만든다")
    void givenUpdateWithoutNewTime_whenUserProvidesNewTime_thenCreatesPreviewFromConversationHistory() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        String conversationId = conversationService.createConversation(account.getId());
        EventResponse before = timedEvent("팀 회의", "2026-08-16T05:00:00Z", "2026-08-16T06:00:00Z");
        EventResponse after = timedEvent("팀 회의", "2026-08-16T06:00:00Z", "2026-08-16T07:00:00Z");
        when(eventService.listEvents(any(), any(), any())).thenReturn(List.of(before));
        when(mutationService.preview(eq(account.getId()), any())).thenReturn(new CalendarMutationPreview(
                CalendarMutationType.UPDATE,
                CalendarMutationScope.EVENT,
                before,
                after
        ));

        // when
        SendCalendarConversationMessageResponse first = send(account, conversationId, "내일 팀 회의를 옮겨줘");
        SendCalendarConversationMessageResponse second = send(account, conversationId, "오후 3시로 옮겨줘");

        // then
        assertResponseMatchesReference(
                "내일 팀 회의를 옮겨줘",
                "The event to update is known, but the requested new time is missing. "
                        + "Ask one concise question for the new time. Do not ask the user to repeat the event title or identify a calendar.",
                first
        );
        assertThat(first.blocks()).noneMatch(block -> block.type() == CalendarAssistantBlockType.MUTATION_PREVIEW);
        assertThat(second.assistantMessage()).isNotBlank();
        assertThat(second.blocks()).extracting(block -> block.type())
                .contains(CalendarAssistantBlockType.MUTATION_PREVIEW);
        assertThat(capturedPreviewRequest().operation()).isEqualTo(CalendarMutationOperation.UPDATE_EVENT);
        verify(mutationService, never()).apply(any(), any());
    }

    @Test
    @DisplayName("여러 Mutation Preview 뒤 대상 없는 적용 요청은 변경 대상을 다시 묻는다")
    void givenMultipleMutationPreviews_whenUserConfirmsWithoutTarget_thenAsksWhichChangeToApply() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        String conversationId = conversationService.createConversation(account.getId());
        EventResponse teamMeeting = timedEvent("팀 회의", "2026-08-16T05:00:00Z", "2026-08-16T06:00:00Z");
        EventResponse movedTeamMeeting = timedEvent("팀 회의", "2026-08-16T06:00:00Z", "2026-08-16T07:00:00Z");
        EventResponse designReview = timedEvent("디자인 리뷰", "2026-08-16T08:00:00Z", "2026-08-16T09:00:00Z");
        when(eventService.listEvents(any(), any(), any()))
                .thenReturn(List.of(teamMeeting))
                .thenReturn(List.of(designReview));
        when(mutationService.preview(eq(account.getId()), any())).thenReturn(
                new CalendarMutationPreview(
                        CalendarMutationType.UPDATE,
                        CalendarMutationScope.EVENT,
                        teamMeeting,
                        movedTeamMeeting
                ),
                new CalendarMutationPreview(
                        CalendarMutationType.DELETE,
                        CalendarMutationScope.EVENT,
                        designReview,
                        null
                )
        );

        // when
        SendCalendarConversationMessageResponse first = send(
                account,
                conversationId,
                "내일 팀 회의를 오후 3시부터 4시까지로 옮겨줘"
        );
        SendCalendarConversationMessageResponse second = send(
                account,
                conversationId,
                "그리고 내일 디자인 리뷰는 삭제해줘"
        );
        SendCalendarConversationMessageResponse confirmation = send(account, conversationId, "적용해줘");

        // then
        assertThat(first.blocks()).extracting(block -> block.type())
                .contains(CalendarAssistantBlockType.MUTATION_PREVIEW);
        assertThat(second.blocks()).extracting(block -> block.type())
                .contains(CalendarAssistantBlockType.MUTATION_PREVIEW);
        assertResponseMatchesReference(
                "Conversation has two unapplied previews: move the team meeting to 3 PM and delete the design review. "
                        + "User says: apply it.",
                "Ask which of the two calendar changes the user wants to apply. "
                        + "Do not apply either change.",
                confirmation
        );
        assertThat(confirmation.blocks()).noneMatch(block -> block.type() == CalendarAssistantBlockType.MUTATION_PREVIEW);
        verify(mutationService, never()).apply(any(), any());
    }

    private SendCalendarConversationMessageResponse send(Account account, String conversationId, String message) {
        SendCalendarConversationMessageResponse response = conversationService.sendMessage(
                account.getId(),
                conversationId,
                message,
                "Asia/Seoul"
        );
        responses.add(response);
        return response;
    }

    private CalendarMutationToolRequest capturedPreviewRequest() {
        ArgumentCaptor<CalendarMutationToolRequest> requestCaptor = ArgumentCaptor.forClass(
                CalendarMutationToolRequest.class
        );
        verify(mutationService).preview(any(), requestCaptor.capture());
        return requestCaptor.getValue();
    }

    private void assertResponseMatchesReference(
            String question,
            String expectedResponse,
            SendCalendarConversationMessageResponse response
    ) {
        EvaluationResponse evaluation = FactCheckingEvaluator.builder(ChatClient.builder(chatModel))
                .build()
                .evaluate(new EvaluationRequest(
                        question,
                        List.of(new Document(expectedResponse)),
                        response.assistantMessage()
                ));
        assertThat(evaluation.isPass())
                .withFailMessage(() -> """
                        Calendar conversation flow evaluation failed.
                        question: %s
                        evaluation reference:
                        %s
                        assistant response:
                        %s
                        judge feedback: %s
                        """.formatted(question, expectedResponse, response, evaluation.getFeedback()))
                .isTrue();
    }

    private void assertConversationHistory(
            Account account,
            String conversationId,
            String firstUserMessage,
            String firstAssistantMessage,
            String secondUserMessage,
            String secondAssistantMessage
    ) {
        Long conversationRecordId = conversationRepository
                .findByConversationIdAndAccount_Id(conversationId, account.getId())
                .orElseThrow()
                .getId();
        assertThat(conversationQueryService.getRecentHistory(conversationRecordId, 20))
                .extracting(message -> message.role())
                .containsExactly(
                        CalendarConversationMessageRole.USER,
                        CalendarConversationMessageRole.ASSISTANT,
                        CalendarConversationMessageRole.USER,
                        CalendarConversationMessageRole.ASSISTANT
                );
        assertThat(conversationQueryService.getRecentHistory(conversationRecordId, 20))
                .extracting(message -> message.text())
                .containsExactly(
                        firstUserMessage,
                        firstAssistantMessage,
                        secondUserMessage,
                        secondAssistantMessage
                );
    }

    private EventResponse timedEvent(String title, String startAt, String endAt) {
        return new EventResponse(
                1L,
                title,
                null,
                Instant.parse(startAt),
                Instant.parse(endAt),
                false,
                "Asia/Seoul",
                false,
                null,
                false,
                null,
                null,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z")
        );
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean(name = "evalClock")
        @Primary
        Clock evalClock() {
            return Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC);
        }
    }
}
