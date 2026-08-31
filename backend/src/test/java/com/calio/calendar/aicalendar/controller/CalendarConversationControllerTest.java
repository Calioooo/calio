package com.calio.calendar.aicalendar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.aicalendar.repository.CalendarConversationMessageRepository;
import com.calio.calendar.aicalendar.repository.CalendarConversationRepository;
import com.calio.calendar.aicalendar.service.SpringAiCalendarAssistantAgent;
import com.calio.calendar.aicalendar.service.CalendarConversationService;
import com.calio.calendar.aicalendar.service.dto.CalendarAssistantAnswer;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-calendar-conversation-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class CalendarConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CalendarConversationRepository conversationRepository;

    @Autowired
    private CalendarConversationMessageRepository messageRepository;

    @Autowired
    private CalendarConversationService conversationService;

    @MockitoBean
    private SpringAiCalendarAssistantAgent assistantAgent;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
    }

    @Test
    @DisplayName("대화 생성은 인증 계정 소유 conversationId만 HTTP 201으로 반환하고 provider를 호출하지 않는다")
    void givenAuthenticatedAccount_whenCreateConversation_thenReturnsOnlyConversationIdWithoutAgentCall()
            throws Exception {
        // when, then
        mockMvc.perform(post("/api/ai/calendar/conversations"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversationId").isString())
                .andExpect(jsonPath("$.*").value(org.hamcrest.Matchers.hasSize(1)));
        verifyNoInteractions(assistantAgent);
    }

    @Test
    @DisplayName("유효한 메시지는 user와 successful assistant 메시지를 저장하고 최소 응답을 반환한다")
    void givenValidMessage_whenSendMessage_thenPersistsSuccessfulExchangeAndReturnsMinimalResponse()
            throws Exception {
        // given
        String conversationId = createConversation();
        when(assistantAgent.answer(any())).thenReturn(CalendarAssistantAnswer.withoutBlocks("일정을 확인했어요."));

        // when
        MvcResult result = mockMvc.perform(post("/api/ai/calendar/conversations/{conversationId}/messages", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"내일 일정 알려줘","timeZone":"Asia/Seoul"}
                                """))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conversationId))
                .andExpect(jsonPath("$.assistantMessage").value("일정을 확인했어요."))
                .andExpect(jsonPath("$.blocks").isEmpty())
                .andExpect(jsonPath("$.*").value(org.hamcrest.Matchers.hasSize(3)))
                .andReturn();

        assertThat(messageRepository.findAll())
                .extracting(message -> message.getText())
                .containsExactlyInAnyOrder("일정을 확인했어요.", "내일 일정 알려줘");
        assertThat(readJson(result).get("assistantMessage").asString()).isEqualTo("일정을 확인했어요.");
    }

    @Test
    @DisplayName("유효하지 않은 timezone은 message 저장과 agent 호출 전에 거부한다")
    void givenInvalidTimeZone_whenSendMessage_thenRejectsBeforePersistenceOrAgentCall() throws Exception {
        // given
        String conversationId = createConversation();

        // when, then
        mockMvc.perform(post("/api/ai/calendar/conversations/{conversationId}/messages", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"내일 일정 알려줘","timeZone":"Invalid/Zone"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_TIME_ZONE"));
        assertThat(messageRepository.count()).isZero();
        verifyNoInteractions(assistantAgent);
    }

    @Test
    @DisplayName("다른 계정의 conversation은 agent 호출과 message 조회 전에 거부한다")
    void givenConversationOwnedByAnotherAccount_whenSendMessage_thenRejectsWithoutAgentCall() throws Exception {
        // given
        Account otherAccount = accountRepository.saveAndFlush(new Account());
        String conversationId = conversationService.createConversation(otherAccount.getId());

        // when, then
        mockMvc.perform(post("/api/ai/calendar/conversations/{conversationId}/messages", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"내일 일정 알려줘","timeZone":"Asia/Seoul"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("AI_CALENDAR_CONVERSATION_NOT_FOUND"));
        assertThat(messageRepository.count()).isZero();
        verifyNoInteractions(assistantAgent);
    }

    @Test
    @DisplayName("provider 실패 시 user 메시지만 보존하고 assistant 메시지는 저장하지 않는다")
    void givenProviderFailure_whenSendMessage_thenKeepsOnlyUserMessage() throws Exception {
        // given
        String conversationId = createConversation();
        when(assistantAgent.answer(any())).thenThrow(
                new CalioException(ErrorCode.AI_CALENDAR_PROVIDER_UNAVAILABLE)
        );

        // when, then
        mockMvc.perform(post("/api/ai/calendar/conversations/{conversationId}/messages", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"내일 일정 알려줘","timeZone":"Asia/Seoul"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Service Unavailable"));
        assertThat(messageRepository.count()).isOne();
        assertThat(messageRepository.findAll()).extracting(message -> message.getText())
                .containsExactly("내일 일정 알려줘");
    }


    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String createConversation() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai/calendar/conversations"))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("conversationId").asString();
    }
}
