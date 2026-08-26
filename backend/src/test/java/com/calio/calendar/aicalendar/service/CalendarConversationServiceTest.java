package com.calio.calendar.aicalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.aicalendar.domain.CalendarConversation;
import com.calio.calendar.aicalendar.domain.CalendarConversationMessageRole;
import com.calio.calendar.aicalendar.domain.CalendarAssistantRequestClassification;
import com.calio.calendar.aicalendar.service.dto.CalendarAssistantAnswer;
import com.calio.calendar.aicalendar.service.dto.CalendarAssistantRequest;
import com.calio.calendar.aicalendar.repository.CalendarConversationMessageRepository;
import com.calio.calendar.aicalendar.repository.CalendarConversationRepository;
import com.calio.calendar.aicalendar.service.dto.CalendarConversationHistoryMessage;
import com.calio.calendar.event.controller.dto.EventResponse;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-calendar-conversation-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CalendarConversationServiceTest {

    @Autowired
    private CalendarConversationService conversationService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CalendarConversationRepository conversationRepository;

    @Autowired
    private CalendarConversationMessageRepository messageRepository;

    @MockitoBean
    private CalendarAssistantRequestClassifier requestClassifier;

    @MockitoBean
    private CalendarAssistantAgent assistantAgent;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("대화는 계정 소유로 생성되고 최신 20개 메시지를 시간순으로 agent history에 제공한다")
    void givenTwentyOneMessages_whenRecordUserMessage_thenReturnsNewestTwentyInChronologicalOrder() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        String conversationId = conversationService.createConversation(account.getId());
        Long conversationRecordId = conversationRecordId(account.getId(), conversationId);

        // when
        List<String> histories = IntStream.rangeClosed(1, 21)
                .mapToObj(index -> conversationService.recordUserMessageAndGetRecentHistory(
                        conversationRecordId,
                        "message-" + index
                ))
                .map(history -> history.stream().map(CalendarConversationHistoryMessage::text).toList())
                .toList()
                .getLast();

        // then
        assertThat(histories).containsExactlyElementsOf(
                IntStream.rangeClosed(2, 21)
                        .mapToObj(index -> "message-" + index)
                        .toList()
        );
    }

    @Test
    @DisplayName("assistant 메시지를 기록하면 ASSISTANT 역할로 저장한다")
    void givenConversation_whenRecordAssistantMessage_thenSavesAssistantRole() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        String conversationId = conversationService.createConversation(account.getId());
        Long conversationRecordId = conversationRecordId(account.getId(), conversationId);

        // when
        conversationService.recordAssistantMessage(conversationRecordId, "일정을 확인했어요.");

        // then
        assertThat(messageRepository.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getRole()).isEqualTo(CalendarConversationMessageRole.ASSISTANT);
            assertThat(message.getText()).isEqualTo("일정을 확인했어요.");
            assertThat(message.getAssistantResponseBlocksJson()).isEqualTo("[]");
        });
    }

    @Test
    @DisplayName("30일 동안 활동하지 않은 대화는 내부 message와 함께 삭제한다")
    void givenInactiveConversation_whenDeleteInactiveConversations_thenDeletesConversationAndMessages() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        String conversationId = conversationService.createConversation(account.getId());
        Long conversationRecordId = conversationRecordId(account.getId(), conversationId);
        conversationService.recordUserMessageAndGetRecentHistory(conversationRecordId, "오래된 메시지");
        CalendarConversation conversation = conversationRepository
                .findByConversationIdAndAccount_Id(conversationId, account.getId())
                .orElseThrow();
        conversation.touch(Instant.parse("2026-06-01T00:00:00Z"));
        conversationRepository.saveAndFlush(conversation);

        // when
        int deletedCount = conversationService.deleteInactiveConversations(
                Instant.parse("2026-07-01T00:00:00Z")
        );

        // then
        assertThat(deletedCount).isOne();
        assertThat(conversationRepository.count()).isZero();
        assertThat(messageRepository.count()).isZero();
    }

    @Test
    @DisplayName("지원 범위 밖 요청은 agent를 호출하지 않고 범위 안내를 assistant 메시지로 저장한다")
    void givenUnsupportedMessage_whenSendMessage_thenRecordsRandomScopeGuideWithoutCallingAgent() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        String conversationId = conversationService.createConversation(account.getId());
        when(requestClassifier.classify("너는 몇 살이야?"))
                .thenReturn(CalendarAssistantRequestClassification.UNSUPPORTED);

        // when
        var response = conversationService.sendMessage(
                account.getId(),
                conversationId,
                "너는 몇 살이야?",
                "Asia/Seoul"
        );

        // then
        verify(assistantAgent, never()).answer(any());
        assertThat(response.blocks()).isEmpty();
        assertThat(response.assistantMessage()).isIn(
                "일정 조회와 빈 시간 찾기를 도와드릴 수 있어요.",
                "캘린더의 일정 확인이나 빈 시간 찾기를 요청해 주세요.",
                "일정 조회와 빈 시간 찾기 기능을 지원해요."
        );
        assertThat(messageRepository.findAll()).satisfiesExactly(
                savedMessage -> {
                    assertThat(savedMessage.getRole()).isEqualTo(CalendarConversationMessageRole.USER);
                    assertThat(savedMessage.getText()).isEqualTo("너는 몇 살이야?");
                    assertThat(savedMessage.getAssistantResponseBlocksJson()).isNull();
                },
                savedMessage -> {
                    assertThat(savedMessage.getRole()).isEqualTo(CalendarConversationMessageRole.ASSISTANT);
                    assertThat(savedMessage.getText()).isEqualTo(response.assistantMessage());
                    assertThat(savedMessage.getAssistantResponseBlocksJson()).isEqualTo("[]");
                }
        );
    }

    @Test
    @DisplayName("지원 요청은 agent에 최근 대화 이력과 함께 전달한다")
    void givenSupportedMessage_whenSendMessage_thenDelegatesToAgent() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        String conversationId = conversationService.createConversation(account.getId());
        when(requestClassifier.classify("안녕"))
                .thenReturn(CalendarAssistantRequestClassification.SUPPORTED);
        when(assistantAgent.answer(any())).thenReturn(new CalendarAssistantAnswer(
                "안녕하세요. 일정 조회와 빈 시간 찾기를 도와드릴게요.",
                List.of(),
                List.of(),
                List.of()
        ));

        // when
        var response = conversationService.sendMessage(account.getId(), conversationId, "안녕", "Asia/Seoul");

        // then
        ArgumentCaptor<CalendarAssistantRequest> requestCaptor = ArgumentCaptor.forClass(
                CalendarAssistantRequest.class
        );
        verify(assistantAgent).answer(requestCaptor.capture());
        assertThat(response.assistantMessage()).isEqualTo("안녕하세요. 일정 조회와 빈 시간 찾기를 도와드릴게요.");
        assertThat(requestCaptor.getValue().history()).extracting(CalendarConversationHistoryMessage::text)
                .containsExactly("안녕");
        assertThat(messageRepository.findAll()).extracting(message -> message.getRole())
                .containsExactly(CalendarConversationMessageRole.USER, CalendarConversationMessageRole.ASSISTANT);
    }

    @Test
    @DisplayName("assistant response block은 메시지와 함께 저장되어 다음 agent history에 전달된다")
    void givenAssistantResponseBlock_whenSendNextMessage_thenProvidesStoredBlockToAgent() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        String conversationId = conversationService.createConversation(account.getId());
        EventResponse event = new EventResponse(
                1L,
                "팀 회의",
                null,
                Instant.parse("2026-08-16T05:00:00Z"),
                Instant.parse("2026-08-16T06:00:00Z"),
                false,
                "Asia/Seoul",
                false,
                null,
                false,
                null,
                null,
                null,
                null
        );
        when(requestClassifier.classify(any()))
                .thenReturn(CalendarAssistantRequestClassification.SUPPORTED);
        when(assistantAgent.answer(any()))
                .thenReturn(
                        new CalendarAssistantAnswer("팀 회의가 있습니다.", List.of(event), List.of(), List.of()),
                        CalendarAssistantAnswer.withoutBlocks("다른 요청도 도와드릴게요.")
                );

        // when
        conversationService.sendMessage(account.getId(), conversationId, "내일 일정 알려줘", "Asia/Seoul");
        conversationService.sendMessage(account.getId(), conversationId, "고마워", "Asia/Seoul");

        // then
        ArgumentCaptor<CalendarAssistantRequest> requestCaptor = ArgumentCaptor.forClass(
                CalendarAssistantRequest.class
        );
        verify(assistantAgent, org.mockito.Mockito.times(2)).answer(requestCaptor.capture());
        CalendarConversationHistoryMessage assistantMessage = requestCaptor.getAllValues().get(1)
                .history()
                .get(1);
        assertThat(assistantMessage.assistantResponseBlocksJson())
                .contains("\"type\":\"EVENTS\"");
    }

    private Long conversationRecordId(Long accountId, String conversationId) {
        return conversationRepository.findByConversationIdAndAccount_Id(conversationId, accountId)
                .orElseThrow()
                .getId();
    }
}
