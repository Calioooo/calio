package com.calio.calendar.groupspace.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:group-space-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class GroupSpaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithAuthenticatedAccount
    @DisplayName("인증 Account가 그룹을 생성하면 OWNER membership, explicit null emoji와 origin-relative Location을 받는다")
    void createGroupReturnsCanonicalContract() throws Exception {
        mockMvc.perform(post("/api/group-spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  Team  ",
                                  "emoji": "",
                                  "nickname": "Owner1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        "/api/group-spaces/[0-9]+"
                )))
                .andExpect(jsonPath("$.name").value("Team"))
                .andExpect(jsonPath("$.emoji").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.myMembership.role").value("OWNER"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString());
    }

    @Test
    @WithAuthenticatedAccount
    @DisplayName("그룹 PATCH body에 field가 없으면 다섯 field VALIDATION_FAILED ProblemDetail을 반환한다")
    void emptyPatchReturnsValidationProblemDetail() throws Exception {
        long groupSpaceId = createGroup();

        mockMvc.perform(patch("/api/group-spaces/{groupSpaceId}", groupSpaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed."))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.*", hasSize(5)));
    }

    @Test
    @DisplayName("invitation preview는 인증 없이 접근하며 잘못된 credential은 인증 오류 대신 validation 오류다")
    void previewIsPublic() throws Exception {
        mockMvc.perform(post("/api/group-invitations/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "credentialType": "LINK_TOKEN",
                                  "credential": "invalid"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @WithAuthenticatedAccount
    @DisplayName("ACTIVE membership 목록 wrapper는 생성한 그룹을 포함한다")
    void listGroupsReturnsNonNullWrapper() throws Exception {
        createGroup();

        mockMvc.perform(get("/api/group-spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupSpaces").isArray())
                .andExpect(jsonPath("$.groupSpaces[0].myMembership.role").value("OWNER"));
    }

    private long createGroup() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/group-spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Team",
                                  "emoji": "🙂",
                                  "nickname": "Owner1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
