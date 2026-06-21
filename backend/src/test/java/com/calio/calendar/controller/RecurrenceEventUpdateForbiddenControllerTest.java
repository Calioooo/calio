package com.calio.calendar.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-recurrence-forbidden-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "calio.recurrence-events.update-enabled=false"
})
@AutoConfigureMockMvc
class RecurrenceEventUpdateForbiddenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("backend authorization policy가 거부하면 반복 일정 전체 수정은 RECURRENCE_EVENT_UPDATE_FORBIDDEN을 받는다")
    void givenBackendAuthorizationDeniesUpdate_whenPatchRecurrenceEvent_thenReturnsRecurrenceEventUpdateForbidden()
            throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent();

        // when
        mockMvc.perform(patch("/api/recurrence-events/{recurrenceId}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Blocked update"
                                }
                                """))
                // then
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("RECURRENCE_EVENT_UPDATE_FORBIDDEN"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    private long createRecurrenceEvent() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recurrenceTitle": "Forbidden target",
                                  "recurrenceDescription": null,
                                  "recurrenceStartDate": "2026-11-26",
                                  "recurrenceEndDate": "2026-11-27",
                                  "recurrenceStartTime": "09:00:00",
                                  "recurrenceEndTime": "10:00:00",
                                  "recurrenceFrequency": "DAILY"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(content).get("recurrenceId").asLong();
    }
}
