package com.calio.calendar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.repository.TaskRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-task-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
    }

    @Test
    @DisplayName("사용자는 taskTitle로 작업을 생성하면 서버 기본 완료 상태와 감사 필드가 포함된 작업을 받는다")
    void givenValidTaskTitle_whenCreateTask_thenReturnsPersistedTaskWithServerManagedDefaults()
            throws Exception {
        // given
        String requestBody = """
                {
                  "taskTitle": "Inbox review",
                  "isCompleted": true,
                  "createdAt": "2000-01-01T00:00:00Z",
                  "updatedAt": "2000-01-01T00:00:00Z"
                }
                """;

        // when
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskId").isNumber())
                .andExpect(jsonPath("$.taskTitle").value("Inbox review"))
                .andExpect(jsonPath("$.isCompleted").value(false))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andReturn();

        JsonNode response = readResponse(result);
        assertThat(response.get("createdAt").asText()).isNotEqualTo("2000-01-01T00:00:00Z");
        assertThat(response.get("updatedAt").asText()).isNotEqualTo("2000-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("사용자는 taskTitle 없이 작업을 생성할 수 없다")
    void givenMissingTaskTitle_whenCreateTask_thenReturnsValidationFailed() throws Exception {
        // when
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("사용자는 null taskTitle로 작업을 생성할 수 없다")
    void givenNullTaskTitle_whenCreateTask_thenReturnsValidationFailed() throws Exception {
        // when
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskTitle": null
                                }
                                """))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("사용자는 공백 taskTitle로 작업을 생성할 수 없다")
    void givenBlankTaskTitle_whenCreateTask_thenReturnsValidationFailed() throws Exception {
        // when
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskTitle": " "
                                }
                                """))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("사용자는 전체 작업 목록을 taskId 오름차순으로 조회한다")
    void givenCreatedTasks_whenListTasks_thenReturnsTasksSortedByTaskIdAscending() throws Exception {
        // given
        long firstTaskId = createTask("First task");
        long secondTaskId = createTask("Second task");
        long thirdTaskId = createTask("Third task");

        // when
        mockMvc.perform(get("/api/tasks"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].taskId").value(firstTaskId))
                .andExpect(jsonPath("$[0].taskTitle").value("First task"))
                .andExpect(jsonPath("$[0].isCompleted").value(false))
                .andExpect(jsonPath("$[1].taskId").value(secondTaskId))
                .andExpect(jsonPath("$[1].taskTitle").value("Second task"))
                .andExpect(jsonPath("$[2].taskId").value(thirdTaskId))
                .andExpect(jsonPath("$[2].taskTitle").value("Third task"));
    }

    private long createTask(String taskTitle) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskTitle": "%s"
                                }
                                """.formatted(taskTitle)))
                .andExpect(status().isCreated())
                .andReturn();

        return readResponse(result).get("taskId").asLong();
    }

    private JsonNode readResponse(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(content);
    }
}
