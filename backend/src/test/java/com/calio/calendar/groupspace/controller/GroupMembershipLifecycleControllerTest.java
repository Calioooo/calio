package com.calio.calendar.groupspace.controller;

import static com.calio.calendar.security.TestAccountSupport.currentAccountId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.domain.AccountAuthToken;
import com.calio.calendar.account.repository.AccountAuthTokenRepository;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.auth.service.AccessTokenEncoder;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.domain.InvitationCredentialType;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupinvitation.service.InvitationCredentialService;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:group-membership-lifecycle-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class GroupMembershipLifecycleControllerTest {

    private static final String LINK_TOKEN = "A".repeat(43);
    private static final String SECOND_LINK_TOKEN = "B".repeat(43);
    private static final String REJOIN_LINK_TOKEN = "C".repeat(43);
    private static final Instant MEMBER_CREATED_AT = Instant.parse("2026-07-30T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountAuthTokenRepository accountAuthTokenRepository;

    @Autowired
    private GroupSpaceRepository groupSpaceRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private GroupInvitationRepository groupInvitationRepository;

    @Autowired
    private InvitationCredentialService credentialService;

    @Autowired
    private AccessTokenEncoder accessTokenEncoder;

    @BeforeEach
    void setUp() {
        groupInvitationRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupSpaceRepository.deleteAll();
        accountAuthTokenRepository.deleteAll();
    }

    @Test
    @DisplayName("초대 수락은 신규 가입에 201 Created, ACTIVE 재수락에 200 OK를 반환한다")
    void acceptReturnsCreatedThenOkForExistingActiveMember() throws Exception {
        // given
        GroupFixture fixture = createGroupFixture();
        createInvitation(fixture.groupSpace(), fixture.ownerMember());
        String joinerToken = createAuthenticatedToken();

        // when
        MvcResult joined = accept(joinerToken, "joiner")
                // then
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/group-spaces/" + fixture.groupSpace().getId()))
                .andExpect(jsonPath("$.joinResult").value("JOINED"))
                .andExpect(jsonPath("$.groupSpace.memberCount").value(2))
                .andReturn();

        JsonNode joinedBody = objectMapper.readTree(joined.getResponse().getContentAsByteArray());
        assertThat(joinedBody.get("membership").get("memberId").asLong())
                .isEqualTo(joinedBody.get("groupSpace").get("myMembership").get("memberId").asLong());

        accept(joinerToken, "changed")
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.joinResult").value("ALREADY_MEMBER"))
                .andExpect(jsonPath("$.groupSpace.memberCount").value(2));
    }

    @Test
    @DisplayName("신규 가입 응답의 memberCount는 inactive membership 이력을 제외한다")
    void acceptMemberCountExcludesInactiveMembershipHistory() throws Exception {
        // given
        GroupFixture fixture = createGroupFixture();
        Account departedAccount = accountRepository.saveAndFlush(new Account());
        GroupMember departedMember = groupMemberRepository.saveAndFlush(
                new GroupMember(
                        fixture.groupSpace(),
                        departedAccount.getId(),
                        "departed",
                        MEMBER_CREATED_AT
                )
        );
        departedMember.deactivate(GroupMemberStatus.LEFT, Instant.now());
        groupMemberRepository.saveAndFlush(departedMember);
        createInvitation(fixture.groupSpace(), fixture.ownerMember());

        // when, then
        accept(createAuthenticatedToken(), "joiner")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupSpace.memberCount").value(2));
    }

    @Test
    @DisplayName("재가입한 Group Space는 statusChangedAt 기준 목록의 첫 항목으로 정렬된다")
    void rejoinedGroupSpaceIsListedFirstByStatusChangedAt() throws Exception {
        // given
        GroupFixture groupA = createGroupFixture();
        GroupFixture groupB = createGroupFixture();
        createInvitation(groupA.groupSpace(), groupA.ownerMember(), LINK_TOKEN);
        createInvitation(groupB.groupSpace(), groupB.ownerMember(), SECOND_LINK_TOKEN);
        String joinerToken = createAuthenticatedToken();
        accept(joinerToken, LINK_TOKEN, "joiner").andExpect(status().isCreated());
        accept(joinerToken, SECOND_LINK_TOKEN, "joiner").andExpect(status().isCreated());

        // when
        mockMvc.perform(delete("/api/group-spaces/{groupSpaceId}/members/me", groupA.groupSpace().getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + joinerToken))
                .andExpect(status().isNoContent());
        createInvitation(groupA.groupSpace(), groupA.ownerMember(), REJOIN_LINK_TOKEN);
        accept(joinerToken, REJOIN_LINK_TOKEN, "rejoined")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joinResult").value("REJOINED"));

        // then
        mockMvc.perform(get("/api/group-spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + joinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupSpaces[0].groupSpaceId").value(groupA.groupSpace().getId()));
    }

    @Test
    @DisplayName("기존 Group Membership 응답은 updatedAt과 statusChangedAt을 함께 제공한다")
    void groupMembershipResponseKeepsLegacyAndLifecycleTimestamps() throws Exception {
        // given
        GroupFixture fixture = createGroupFixture();

        // when, then
        MvcResult result = mockMvc.perform(get("/api/group-spaces/{groupSpaceId}", fixture.groupSpace().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myMembership.updatedAt").isString())
                .andExpect(jsonPath("$.myMembership.statusChangedAt").isString())
                .andReturn();
        JsonNode membership = objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("myMembership");
        assertThat(membership.get("updatedAt").asString())
                .isEqualTo(membership.get("statusChangedAt").asString());
    }

    @Test
    @DisplayName("ACTIVE membership이 없는 호출자는 member 목록에서 Group Space를 404로 은닉받는다")
    void memberListConcealsGroupFromNonMember() throws Exception {
        // given
        Account anotherOwner = accountRepository.saveAndFlush(new Account());
        GroupSpace hiddenGroup = groupSpaceRepository.saveAndFlush(
                new GroupSpace(anotherOwner.getId(), "Hidden", null)
        );
        groupMemberRepository.saveAndFlush(
                new GroupMember(hiddenGroup, anotherOwner.getId(), "owner", MEMBER_CREATED_AT)
        );

        // when, then
        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}/members", hiddenGroup.getId()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("GROUP_SPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.errorCode").value("GROUP_SPACE_NOT_FOUND"))
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("초대 수락 request validation 오류는 VALIDATION_FAILED problem 응답을 반환한다")
    void acceptValidationFailureUsesProblemErrorCode() throws Exception {
        // when, then
        mockMvc.perform(post("/api/group-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "credentialType": "UNKNOWN",
                                  "credential": "invalid",
                                  "nickname": "joiner"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    private org.springframework.test.web.servlet.ResultActions accept(
            String accessToken,
            String nickname
    ) throws Exception {
        return accept(accessToken, LINK_TOKEN, nickname);
    }

    private org.springframework.test.web.servlet.ResultActions accept(
            String accessToken,
            String linkToken,
            String nickname
    ) throws Exception {
        return mockMvc.perform(post("/api/group-invitations/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "credentialType": "LINK_TOKEN",
                          "credential": "%s",
                          "nickname": "%s"
                        }
                        """.formatted(linkToken, nickname)));
    }

    private String createAuthenticatedToken() {
        Account account = accountRepository.saveAndFlush(new Account());
        String accessToken = accessTokenEncoder.generateRawToken();
        accountAuthTokenRepository.saveAndFlush(
                new AccountAuthToken(account, accessTokenEncoder.hash(accessToken))
        );
        return accessToken;
    }

    private GroupFixture createGroupFixture() {
        Long ownerAccountId = currentAccountId();
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(ownerAccountId, "Shared", null)
        );
        GroupMember ownerMember = groupMemberRepository.saveAndFlush(
                new GroupMember(groupSpace, ownerAccountId, "owner", MEMBER_CREATED_AT)
        );
        return new GroupFixture(groupSpace, ownerMember);
    }

    private void createInvitation(GroupSpace groupSpace, GroupMember ownerMember) {
        createInvitation(groupSpace, ownerMember, LINK_TOKEN);
    }

    private void createInvitation(
            GroupSpace groupSpace,
            GroupMember ownerMember,
            String linkToken
    ) {
        groupInvitationRepository.saveAndFlush(new GroupInvitation(
                groupSpace.getId(),
                ownerMember.getId(),
                credentialService.hashValidated(InvitationCredentialType.LINK_TOKEN, linkToken),
                inviteCodeHash(linkToken),
                Instant.now().plusSeconds(3600)
        ));
    }

    private byte[] inviteCodeHash(String linkToken) {
        byte[] hash = new byte[32];
        hash[0] = (byte) linkToken.charAt(0);
        return hash;
    }

    private record GroupFixture(GroupSpace groupSpace, GroupMember ownerMember) {
    }
}
