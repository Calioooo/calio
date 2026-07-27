package com.calio.calendar.groupspace.controller;

import static com.calio.calendar.security.TestAccountSupport.currentAccountId;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
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
        "spring.datasource.url=jdbc:h2:mem:group-space-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class GroupSpaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private GroupSpaceRepository groupSpaceRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @BeforeEach
    void clearGroupSpaces() {
        groupMemberRepository.deleteAllInBatch();
        groupSpaceRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("생성은 OWNER membership, ACTIVE memberCount, nullable emoji와 Location을 반환한다")
    void givenValidRequest_whenCreate_thenReturnsCreatedGroupSpaceContract() throws Exception {
        mockMvc.perform(post("/api/group-spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  가족  ",
                                  "nickname": "진"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        "/api/group-spaces/[0-9]+"
                )))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("가족"))
                .andExpect(jsonPath("$.emoji").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.myMembership.memberId").isNumber())
                .andExpect(jsonPath("$.myMembership.nickname").value("진"))
                .andExpect(jsonPath("$.myMembership.role").value("OWNER"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andExpect(jsonPath("$.ownerAccountId").doesNotExist());
    }

    @Test
    @DisplayName("목록은 named wrapper를 사용하고 ACTIVE membership이 없으면 빈 배열을 반환한다")
    void givenNoMembership_whenList_thenReturnsEmptyNamedWrapper() throws Exception {
        mockMvc.perform(get("/api/group-spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupSpaces").isArray())
                .andExpect(jsonPath("$.groupSpaces", hasSize(0)));
    }

    @Test
    @DisplayName("한 Account가 여러 Group Space를 소유하면 최신 membership과 group ID 순서로 목록을 반환한다")
    void givenMultipleOwnedGroups_whenList_thenReturnsNewestMembershipFirst() throws Exception {
        long firstGroupSpaceId = createGroupSpace("첫 그룹", null);
        long secondGroupSpaceId = createGroupSpace("둘째 그룹", "😀");

        mockMvc.perform(get("/api/group-spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupSpaces", hasSize(2)))
                .andExpect(jsonPath("$.groupSpaces[0].id").value(secondGroupSpaceId))
                .andExpect(jsonPath("$.groupSpaces[1].id").value(firstGroupSpaceId))
                .andExpect(jsonPath("$.groupSpaces[0].myMembership.role").value("OWNER"));
    }

    @Test
    @DisplayName("PATCH는 field omission과 emoji explicit null을 구분한다")
    void givenExplicitNullEmoji_whenPatch_thenRemovesEmojiAndKeepsName() throws Exception {
        long groupSpaceId = createGroupSpace("친구", "😀");

        mockMvc.perform(patch("/api/group-spaces/{groupSpaceId}", groupSpaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("친구"))
                .andExpect(jsonPath("$.emoji").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("PATCH field가 하나도 없으면 five-field VALIDATION_FAILED ProblemDetail을 반환한다")
    void givenEmptyPatch_whenPatch_thenReturnsValidationProblemDetail() throws Exception {
        long groupSpaceId = createGroupSpace("친구", null);

        mockMvc.perform(patch("/api/group-spaces/{groupSpaceId}", groupSpaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("ACTIVE MEMBER의 OWNER 전용 요청은 GROUP_OWNER_REQUIRED로 거절한다")
    void givenActiveMember_whenDelete_thenReturnsOwnerRequired() throws Exception {
        Account owner = accountRepository.saveAndFlush(new Account());
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(new GroupSpace("타인 그룹", null, owner.getId()));
        groupMemberRepository.saveAndFlush(new GroupMember(groupSpace.getId(), owner.getId(), "owner"));
        groupMemberRepository.saveAndFlush(new GroupMember(groupSpace.getId(), currentAccountId(), "member"));

        mockMvc.perform(delete("/api/group-spaces/{groupSpaceId}", groupSpace.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("GROUP_OWNER_REQUIRED"));
    }

    @Test
    @DisplayName("비멤버의 상세 요청은 존재 여부와 관계없이 GROUP_SPACE_NOT_FOUND를 반환한다")
    void givenNonMember_whenGet_thenHidesExistence() throws Exception {
        Account owner = accountRepository.saveAndFlush(new Account());
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(new GroupSpace("비공개", null, owner.getId()));
        groupMemberRepository.saveAndFlush(new GroupMember(groupSpace.getId(), owner.getId(), "owner2"));

        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}", groupSpace.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GROUP_SPACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("OWNER가 삭제하면 membership과 Group Space가 hard-delete되고 이후 접근은 숨겨진다")
    void givenOwner_whenDelete_thenHardDeletesAggregate() throws Exception {
        long groupSpaceId = createGroupSpace("삭제 그룹", null);

        mockMvc.perform(delete("/api/group-spaces/{groupSpaceId}", groupSpaceId))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Location"));

        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}", groupSpaceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GROUP_SPACE_NOT_FOUND"));
    }

    private long createGroupSpace(String name, String emoji) throws Exception {
        String request = objectMapper.writeValueAsString(new CreateRequest(name, emoji, "me"));
        MvcResult result = mockMvc.perform(post("/api/group-spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private record CreateRequest(String name, String emoji, String nickname) {
    }
}
