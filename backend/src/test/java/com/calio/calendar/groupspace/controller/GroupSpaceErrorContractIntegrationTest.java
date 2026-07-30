package com.calio.calendar.groupspace.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.constraints.Pattern;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:group-space-error-contract-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import({
        AuthenticatedAccountMockMvcTestConfig.class,
        GroupSpaceErrorContractIntegrationTest.ErrorContractController.class
})
class GroupSpaceErrorContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Group Space CalioException은 공통 ErrorProblemDetail 계약으로 처리된다")
    void calioExceptionUsesGlobalProblemDetailContract() throws Exception {
        expectProblemDetail(
                mockMvc.perform(get("/api/group-spaces/error-contract/calio")),
                ErrorCode.GROUP_SPACE_NOT_FOUND,
                "/api/group-spaces/error-contract/calio"
        );
    }

    @Test
    @DisplayName("Group Space validation 오류는 공통 ErrorProblemDetail 계약으로 처리된다")
    void validationExceptionUsesGlobalProblemDetailContract() throws Exception {
        expectProblemDetail(
                mockMvc.perform(get("/api/group-spaces/error-contract/validation/invalid")),
                ErrorCode.VALIDATION_FAILED,
                "/api/group-spaces/error-contract/validation/invalid"
        );
    }

    @Test
    @DisplayName("Group Space 예상하지 못한 오류는 공통 500 ProblemDetail 계약으로 처리된다")
    void unexpectedExceptionUsesGlobalProblemDetailContract() throws Exception {
        mockMvc.perform(get("/api/group-spaces/error-contract/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.status").value(500));
    }

    @Test
    @DisplayName("미인증 Group Space 요청은 AuthenticationEntryPoint의 공통 ProblemDetail 계약을 사용한다")
    void unauthenticatedRequestUsesAuthenticationEntryPointProblemDetailContract() throws Exception {
        expectProblemDetail(
                mockMvc.perform(get("/api/group-spaces/error-contract/calio").with(anonymous())),
                ErrorCode.AUTH_TOKEN_REQUIRED,
                "/api/group-spaces/error-contract/calio"
        );
    }

    private void expectProblemDetail(
            ResultActions actions,
            ErrorCode errorCode,
            String instance
    ) throws Exception {
        actions.andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value(errorCode.name()))
                .andExpect(jsonPath("$.status").value(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.detail").value(errorCode.getDefaultMessage()))
                .andExpect(jsonPath("$.instance").value(instance))
                .andExpect(jsonPath("$.errorCode").value(errorCode.name()))
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @RestController
    @Validated
    static class ErrorContractController {

        @GetMapping("/api/group-spaces/error-contract/calio")
        void throwCalioException() {
            throw new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
        }

        @GetMapping("/api/group-spaces/error-contract/validation/{value}")
        void validateValue(@PathVariable("value") @Pattern(regexp = "valid") String value) {
        }

        @GetMapping("/api/group-spaces/error-contract/unexpected")
        void throwUnexpectedException() {
            throw new IllegalStateException("test failure");
        }
    }
}
