package com.calio.calendar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.repository.TaskRepository;
import com.calio.calendar.repository.entity.Task;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
    @DisplayName("새로 생성한 할 일의 완료 상태는 기본적으로 미완료이다")
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
                .andExpect(jsonPath("$.completedAt").value(nullValue()))
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
    @DisplayName("사용자는 활성 작업 목록을 taskId 오름차순으로 조회한다")
    void givenCreatedTasks_whenListTasks_thenReturnsActiveTasksSortedByTaskIdAscending() throws Exception {
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
                .andExpect(jsonPath("$[0].completedAt").value(nullValue()))
                .andExpect(jsonPath("$[1].taskId").value(secondTaskId))
                .andExpect(jsonPath("$[1].taskTitle").value("Second task"))
                .andExpect(jsonPath("$[2].taskId").value(thirdTaskId))
                .andExpect(jsonPath("$[2].taskTitle").value("Third task"));
    }

    @Test
    @DisplayName("DELETE /api/tasks/{taskId}는 request body 없이 작업을 완료하고 completedAt을 기록한다")
    void givenActiveTask_whenCompleteTask_thenReturnsCompletedTaskAndPersistsCompletedAt() throws Exception {
        // given
        long taskId = createTask("Complete me");

        // when
        MvcResult result = mockMvc.perform(delete("/api/tasks/{taskId}", taskId))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.taskTitle").value("Complete me"))
                .andExpect(jsonPath("$.isCompleted").value(true))
                .andExpect(jsonPath("$.completedAt").isString())
                .andReturn();

        JsonNode response = readResponse(result);
        Task task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.isCompleted()).isTrue();
        assertThat(task.getCompletedAt()).isEqualTo(Instant.parse(response.get("completedAt").asText()));
    }

    @Test
    @DisplayName("이미 완료된 작업을 다시 완료하면 기존 completedAt을 보존한다")
    void givenCompletedTask_whenCompleteTaskAgain_thenPreservesCompletedAt() throws Exception {
        // given
        Instant originalCompletedAt = Instant.parse("2026-01-01T00:00:00Z");
        Task task = saveCompletedTask("Already completed", originalCompletedAt);

        // when
        mockMvc.perform(delete("/api/tasks/{taskId}", task.getTaskId()))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isCompleted").value(true))
                .andExpect(jsonPath("$.completedAt").value("2026-01-01T00:00:00Z"));

        Task persistedTask = taskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(persistedTask.getCompletedAt()).isEqualTo(originalCompletedAt);
    }

    @Test
    @DisplayName("PATCH /api/tasks/{taskId}/uncomplete는 request body 없이 작업을 활성 상태로 되돌린다")
    void givenCompletedTask_whenUncompleteTask_thenReturnsActiveTaskAndClearsCompletedAt() throws Exception {
        // given
        Task task = saveCompletedTask("Uncomplete me", Instant.parse("2026-01-01T00:00:00Z"));

        // when
        mockMvc.perform(patch("/api/tasks/{taskId}/uncomplete", task.getTaskId()))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(task.getTaskId()))
                .andExpect(jsonPath("$.taskTitle").value("Uncomplete me"))
                .andExpect(jsonPath("$.isCompleted").value(false))
                .andExpect(jsonPath("$.completedAt").value(nullValue()));

        Task persistedTask = taskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(persistedTask.isCompleted()).isFalse();
        assertThat(persistedTask.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("이미 활성 상태인 작업을 uncomplete해도 미완료 상태와 null completedAt을 유지한다")
    void givenActiveTask_whenUncompleteTask_thenKeepsActiveState() throws Exception {
        // given
        long taskId = createTask("Already active");

        // when
        mockMvc.perform(patch("/api/tasks/{taskId}/uncomplete", taskId))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.isCompleted").value(false))
                .andExpect(jsonPath("$.completedAt").value(nullValue()));

        Task task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.isCompleted()).isFalse();
        assertThat(task.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("완료된 작업은 활성 목록에서 제외되고 uncomplete 후 다시 포함된다")
    void givenCompletedTask_whenListTasks_thenExcludesUntilUncompleted() throws Exception {
        // given
        long firstTaskId = createTask("First task");
        long secondTaskId = createTask("Second task");
        mockMvc.perform(delete("/api/tasks/{taskId}", firstTaskId))
                .andExpect(status().isOk());

        // when, then
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].taskId").value(secondTaskId));

        // when
        mockMvc.perform(patch("/api/tasks/{taskId}/uncomplete", firstTaskId))
                .andExpect(status().isOk());

        // then
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].taskId").value(firstTaskId))
                .andExpect(jsonPath("$[1].taskId").value(secondTaskId));
    }

    @Test
    @DisplayName("없는 작업을 완료하면 TASK_NOT_FOUND를 반환한다")
    void givenMissingTaskId_whenCompleteTask_thenReturnsTaskNotFound() throws Exception {
        // when
        mockMvc.perform(delete("/api/tasks/{taskId}", 999999L))
                // then
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Task not found."));
    }

    @Test
    @DisplayName("없는 작업을 uncomplete하면 TASK_NOT_FOUND를 반환한다")
    void givenMissingTaskId_whenUncompleteTask_thenReturnsTaskNotFound() throws Exception {
        // when
        mockMvc.perform(patch("/api/tasks/{taskId}/uncomplete", 999999L))
                // then
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Task not found."));
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

    private Task saveCompletedTask(String taskTitle, Instant completedAt) {
        Task task = new Task(taskTitle);
        task.complete(completedAt);
        return taskRepository.saveAndFlush(task);
    }

    private JsonNode readResponse(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(content);
    }
}
