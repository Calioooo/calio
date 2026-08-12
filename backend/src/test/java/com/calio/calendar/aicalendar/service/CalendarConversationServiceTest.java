package com.calio.calendar.aicalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.aicalendar.domain.CalendarConversation;
import com.calio.calendar.aicalendar.domain.CalendarConversationMessageRole;
import com.calio.calendar.aicalendar.repository.CalendarConversationMessageRepository;
import com.calio.calendar.aicalendar.repository.CalendarConversationRepository;
import com.calio.calendar.aicalendar.service.dto.CalendarConversationHistoryMessage;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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

        // when
        List<String> histories = IntStream.rangeClosed(1, 21)
                .mapToObj(index -> conversationService.recordUserMessage(
                        account.getId(),
                        conversationId,
                        "message-" + index
                ))
                .map(turn -> turn.history().stream().map(CalendarConversationHistoryMessage::text).toList())
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
    @DisplayName("다른 계정은 대화에 메시지를 저장할 수 없다")
    void givenConversationOwnedByAnotherAccount_whenRecordUserMessage_thenRejectsBeforePersisting() {
        // given
        Account owner = accountRepository.saveAndFlush(new Account());
        Account otherAccount = accountRepository.saveAndFlush(new Account());
        String conversationId = conversationService.createConversation(owner.getId());

        // when, then
        assertThatThrownBy(() -> conversationService.recordUserMessage(
                otherAccount.getId(),
                conversationId,
                "다른 계정의 메시지"
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_CALENDAR_CONVERSATION_NOT_FOUND)
        );
        assertThat(messageRepository.count()).isZero();
    }

    @Test
    @DisplayName("assistant 메시지를 기록하면 ASSISTANT 역할로 저장한다")
    void givenConversation_whenRecordAssistantMessage_thenSavesAssistantRole() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        String conversationId = conversationService.createConversation(account.getId());

        // when
        conversationService.recordAssistantMessage(account.getId(), conversationId, "일정을 확인했어요.");

        // then
        assertThat(messageRepository.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getRole()).isEqualTo(CalendarConversationMessageRole.ASSISTANT);
            assertThat(message.getText()).isEqualTo("일정을 확인했어요.");
        });
    }

    @Test
    @DisplayName("30일 동안 활동하지 않은 대화는 내부 message와 함께 삭제한다")
    void givenInactiveConversation_whenDeleteInactiveConversations_thenDeletesConversationAndMessages() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        String conversationId = conversationService.createConversation(account.getId());
        conversationService.recordUserMessage(account.getId(), conversationId, "오래된 메시지");
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
}
