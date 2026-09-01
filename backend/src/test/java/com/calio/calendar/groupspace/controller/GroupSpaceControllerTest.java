package com.calio.calendar.groupspace.controller;

import static com.calio.calendar.security.TestAccountSupport.currentAccountId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:group-space-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
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

    @Autowired
    private TagRepository tagRepository;

    @BeforeEach
    void setUp() {
        groupMemberRepository.deleteAll();
        tagRepository.deleteAll();
        groupSpaceRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 Account는 Group Space와 OWNER membership을 생성하고 origin-relative Location을 받는다")
    void createReturnsOwnerDetailAndLocation() throws Exception {
        // given
        Long actorAccountId = currentAccountId();

        // when
        MvcResult result = mockMvc.perform(post("/api/group-spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  Calio é  ",
                                  "emoji": null,
                                  "nickname": "주인"
                                }
                                """))
                // then
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupSpaceId").isNumber())
                .andExpect(jsonPath("$.name").value("Calio é"))
                .andExpect(jsonPath("$.emoji").value(nullValue()))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.myMembership.nickname").value("주인"))
                .andExpect(jsonPath("$.myMembership.role").value("OWNER"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andExpect(jsonPath("$.ownerAccountId").doesNotExist())
                .andReturn();

        JsonNode response = readResponse(result);
        long groupSpaceId = response.get("groupSpaceId").asLong();
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/api/group-spaces/" + groupSpaceId);
        assertThat(response.get("createdAt").asString()).endsWith("Z");
        assertThat(response.get("updatedAt").asString()).endsWith("Z");
        assertThat(groupSpaceRepository.findById(groupSpaceId).orElseThrow().getOwnerAccountId())
                .isEqualTo(actorAccountId);
    }

    @Test
    @DisplayName("목록은 ACTIVE membership이 있는 Group Space를 named wrapper와 최신 membership 순서로 반환한다")
    void listReturnsActiveMembershipsInRequiredOrder() throws Exception {
        // given
        createGroup("First", "first");
        long secondId = createGroup("Second", "second");

        // when, then
        mockMvc.perform(get("/api/group-spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupSpaces", hasSize(2)))
                .andExpect(jsonPath("$.groupSpaces[0].groupSpaceId").value(secondId))
                .andExpect(jsonPath("$.groupSpaces[0].myMembership.role").value("OWNER"))
                .andExpect(jsonPath("$.groupSpaces[1].name").value("First"));
    }

    @Test
    @DisplayName("ACTIVE 비멤버에게 Group Space 상세 존재를 숨긴다")
    void detailHidesGroupFromNonMember() throws Exception {
        // given
        Account anotherAccount = accountRepository.saveAndFlush(new Account());
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(anotherAccount.getId(), "Hidden", null)
        );
        groupMemberRepository.saveAndFlush(
                new GroupMember(groupSpace, anotherAccount.getId(), "owner", Instant.now())
        );

        // when, then
        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}", groupSpace.getId()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("GROUP_SPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Group space not found."))
                .andExpect(jsonPath("$.instance").value("/api/group-spaces/" + groupSpace.getId()))
                .andExpect(jsonPath("$.errorCode").value("GROUP_SPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("PATCH는 필수 name을 수정하고 explicit null emoji를 제거한다")
    void patchUpdatesRequiredNameAndClearsExplicitNullEmoji() throws Exception {
        // given
        long groupSpaceId = createGroup("Original", "owner", "👩🏽‍💻");

        // when, then
        mockMvc.perform(patch("/api/group-spaces/{groupSpaceId}", groupSpaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Renamed",
                                  "emoji": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.emoji").value(nullValue()));

        GroupSpace persisted = groupSpaceRepository.findById(groupSpaceId).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Renamed");
        assertThat(persisted.getEmoji()).isNull();
    }

    @Test
    @DisplayName("PATCH에서 emoji를 생략하면 null로 갱신한다")
    void patchTreatsOmittedEmojiAsNull() throws Exception {
        // given
        long groupSpaceId = createGroup("Original", "owner", "😀");

        // when, then
        mockMvc.perform(patch("/api/group-spaces/{groupSpaceId}", groupSpaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Renamed"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.emoji").value(nullValue()));

        assertThat(groupSpaceRepository.findById(groupSpaceId).orElseThrow().getEmoji()).isNull();
    }

    @Test
    @DisplayName("PATCH는 explicit empty string emoji를 null로 저장한다")
    void patchCanonicalizesEmptyEmojiToNull() throws Exception {
        // given
        long groupSpaceId = createGroup("Original", "owner", "😀");

        // when, then
        mockMvc.perform(patch("/api/group-spaces/{groupSpaceId}", groupSpaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Original",
                                  "emoji": ""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emoji").value(nullValue()));

        assertThat(groupSpaceRepository.findById(groupSpaceId).orElseThrow().getEmoji()).isNull();
    }

    @Test
    @DisplayName("PATCH는 non-empty emoji의 whitespace와 Unicode 원문을 그대로 저장한다")
    void patchPreservesOpaqueEmoji() throws Exception {
        // given
        long groupSpaceId = createGroup("Original", "owner");
        String opaqueEmoji = " 👩🏽‍💻 e\u0301 ";

        // when
        mockMvc.perform(patch("/api/group-spaces/{groupSpaceId}", groupSpaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", "Original",
                                "emoji", opaqueEmoji
                        ))))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emoji").value(opaqueEmoji));

        assertThat(groupSpaceRepository.findById(groupSpaceId).orElseThrow().getEmoji())
                .isEqualTo(opaqueEmoji);
    }

    @Test
    @DisplayName("PATCH는 name을 생략하면 VALIDATION_FAILED를 반환한다")
    void patchRequiresName() throws Exception {
        // given
        long groupSpaceId = createGroup("Original", "owner");

        // when, then
        mockMvc.perform(patch("/api/group-spaces/{groupSpaceId}", groupSpaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("ACTIVE MEMBER는 OWNER 전용 수정에서 GROUP_OWNER_REQUIRED를 받는다")
    void activeNonOwnerCannotPatch() throws Exception {
        // given
        Account owner = accountRepository.saveAndFlush(new Account());
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(owner.getId(), "Shared", null)
        );
        groupMemberRepository.saveAndFlush(
                new GroupMember(groupSpace, owner.getId(), "owner", Instant.now())
        );
        groupMemberRepository.saveAndFlush(
                new GroupMember(groupSpace, currentAccountId(), "member", Instant.now())
        );

        // when, then
        mockMvc.perform(patch("/api/group-spaces/{groupSpaceId}", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Denied"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("GROUP_OWNER_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("Group owner permission is required."));
    }

    @Test
    @DisplayName("OWNER delete는 GroupMember와 Group Space를 hard-delete하고 body 없는 204를 반환한다")
    void ownerDeleteHardDeletesGroupData() throws Exception {
        // given
        long groupSpaceId = createGroup("Delete me", "owner");

        // when, then
        mockMvc.perform(delete("/api/group-spaces/{groupSpaceId}", groupSpaceId))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Location"));

        assertThat(groupSpaceRepository.existsById(groupSpaceId)).isFalse();
        assertThat(groupMemberRepository.count()).isZero();
    }

    @Test
    @DisplayName("64 Unicode code point를 초과한 emoji는 VALIDATION_FAILED를 반환한다")
    void oversizedEmojiIsRejected() throws Exception {
        // when, then
        mockMvc.perform(post("/api/group-spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", "Emoji boundary",
                                "emoji", "😀".repeat(65),
                                "nickname", "owner"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    private long createGroup(String name, String nickname) throws Exception {
        return createGroup(name, nickname, null);
    }

    private long createGroup(String name, String nickname, String emoji) throws Exception {
        String emojiJson = emoji == null ? "null" : objectMapper.writeValueAsString(emoji);
        MvcResult result = mockMvc.perform(post("/api/group-spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "emoji": %s,
                                  "nickname": "%s"
                                }
                                """.formatted(name, emojiJson, nickname)))
                .andExpect(status().isCreated())
                .andReturn();
        return readResponse(result).get("groupSpaceId").asLong();
    }

    private JsonNode readResponse(MvcResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
