package com.calio.calendar.groupinvitation.controller;

import static com.calio.calendar.security.TestAccountSupport.currentAccountId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.calio.calendar.groupinvitation.service.GroupInvitationService;
import com.calio.calendar.groupinvitation.service.InvitationCredentialService;
import com.calio.calendar.groupspace.controller.dto.CreateGroupSpaceRequest;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.groupspace.service.GroupMembershipService;
import com.calio.calendar.groupspace.service.GroupSpaceService;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:group-invitation-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import({
        AuthenticatedAccountMockMvcTestConfig.class,
        GroupInvitationControllerTest.FixedClockConfig.class
})
class GroupInvitationControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GroupSpaceService groupSpaceService;

    @Autowired
    private GroupInvitationRepository invitationRepository;

    @Autowired
    private GroupMemberRepository memberRepository;

    @Autowired
    private GroupSpaceRepository groupSpaceRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountAuthTokenRepository accountAuthTokenRepository;

    @Autowired
    private AccessTokenEncoder accessTokenEncoder;

    @Autowired
    private InvitationCredentialService credentialService;

    @Autowired
    private GroupInvitationService invitationService;

    @Autowired
    private GroupMembershipService groupMembershipService;

    @BeforeEach
    void setUp() {
        accountAuthTokenRepository.deleteAll();
        invitationRepository.deleteAll();
        memberRepository.deleteAll();
        groupSpaceRepository.deleteAll();
    }

    @Test
    @DisplayName("ACTIVE member는 invitation을 발급·목록·preview하고 issuer scope로 폐기한다")
    void invitationLifecycleContract() throws Exception {
        // given
        long groupSpaceId = createGroup();

        // when
        MvcResult issueResult = mockMvc.perform(post(
                        "/api/group-spaces/{groupSpaceId}/invitations",
                        groupSpaceId
                ))
                // then
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitationId").isNumber())
                .andExpect(jsonPath("$.inviteUrl").value(
                        org.hamcrest.Matchers.startsWith("https://calio.app/invite/")
                ))
                .andExpect(jsonPath("$.inviteCode").value(
                        org.hamcrest.Matchers.matchesPattern(
                                "[0-9ABCDEFGHJKMNPQRSTVWXYZ]{4}(-[0-9ABCDEFGHJKMNPQRSTVWXYZ]{4}){3}"
                        )
                ))
                .andExpect(jsonPath("$.expiresAt").value("2026-07-29T08:00:00Z"))
                .andExpect(jsonPath("$.linkToken").doesNotExist())
                .andExpect(jsonPath("$.linkTokenHash").doesNotExist())
                .andReturn();

        JsonNode issued = read(issueResult);
        long invitationId = issued.get("invitationId").asLong();
        String inviteUrl = issued.get("inviteUrl").asString();
        String linkToken = inviteUrl.substring(inviteUrl.lastIndexOf('/') + 1);
        String inviteCode = issued.get("inviteCode").asString();
        assertThat(issueResult.getResponse().getHeader("Location"))
                .isEqualTo("/api/group-spaces/%d/invitations/%d"
                        .formatted(groupSpaceId, invitationId));

        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}/invitations", groupSpaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitations", hasSize(1)))
                .andExpect(jsonPath("$.invitations[0].invitationId").value(invitationId))
                .andExpect(jsonPath("$.invitations[0].expiresAt")
                        .value("2026-07-29T08:00:00Z"))
                .andExpect(jsonPath("$.invitations[0].inviteCode").doesNotExist());

        preview(linkToken, "LINK_TOKEN");
        preview(inviteCode.toLowerCase(), "CODE");

        mockMvc.perform(delete(
                        "/api/group-spaces/{groupSpaceId}/invitations/{invitationId}",
                        groupSpaceId,
                        invitationId
                ))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/group-invitations/preview")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody("LINK_TOKEN", linkToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GROUP_INVITATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("사용자는 인증 토큰 없이 그룹 초대 정보를 미리 볼 수 있다")
    void previewIsPublicAndNotCacheable() throws Exception {
        // given
        long groupSpaceId = createGroup();
        String inviteCode = issueInviteCode(groupSpaceId);

        // when, then
        mockMvc.perform(post("/api/group-invitations/preview")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody("CODE", inviteCode)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.name").value("Invitation group"))
                .andExpect(jsonPath("$.emoji").value(nullValue()))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.expiresAt").value("2026-07-29T08:00:00Z"))
                .andExpect(jsonPath("$.*", hasSize(4)));
    }

    @Test
    @DisplayName("사용자는 잘못된 인증 토큰이 있어도 그룹 초대 정보를 미리 볼 수 있다")
    void previewAllowsInvalidAuthenticationToken() throws Exception {
        // given
        long groupSpaceId = createGroup();
        String inviteCode = issueInviteCode(groupSpaceId);

        // when, then
        mockMvc.perform(post("/api/group-invitations/preview")
                        .with(anonymous())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody("CODE", inviteCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Invitation group"));
    }

    @Test
    @DisplayName("사용자는 해지된 인증 토큰이 있어도 그룹 초대 정보를 미리 볼 수 있다")
    void previewAllowsRevokedAuthenticationToken() throws Exception {
        // given
        long groupSpaceId = createGroup();
        String inviteCode = issueInviteCode(groupSpaceId);
        String rawToken = "revoked-preview-token";
        Account account = accountRepository.saveAndFlush(new Account());
        AccountAuthToken authToken = new AccountAuthToken(
                account,
                accessTokenEncoder.hash(rawToken)
        );
        authToken.revoke(NOW.minusSeconds(1));
        accountAuthTokenRepository.saveAndFlush(authToken);

        // when, then
        mockMvc.perform(post("/api/group-invitations/preview")
                        .with(anonymous())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody("CODE", inviteCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Invitation group"));
    }

    @Test
    @DisplayName("invalid credentialType과 credential 형식은 VALIDATION_FAILED로 통합한다")
    void invalidPreviewInputReturnsValidationFailed() throws Exception {
        mockMvc.perform(post("/api/group-invitations/preview")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody("UNKNOWN", "secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.instance").value("/api/group-invitations/preview"))
                .andExpect(jsonPath("$.*", hasSize(6)));

        mockMvc.perform(post("/api/group-invitations/preview")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody("LINK_TOKEN", "secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Validation failed."))
                .andExpect(jsonPath("$.instance").value("/api/group-invitations/preview"))
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("Group Space 삭제 transaction은 invitation을 membership보다 먼저 hard-delete한다")
    void groupDeletionRemovesInvitationsBeforeMemberships() {
        // given
        Long actorAccountId = currentAccountId();
        long groupSpaceId = createGroup();
        groupInvitationIssue(groupSpaceId);

        // when
        groupSpaceService.delete(actorAccountId, groupSpaceId);

        // then
        assertThat(invitationRepository.count()).isZero();
        assertThat(memberRepository.count()).isZero();
        assertThat(groupSpaceRepository.count()).isZero();
    }

    @Test
    @DisplayName("유일한 OWNER 탈퇴는 invitation과 membership, Group Space를 함께 hard-delete한다")
    void soleOwnerLeaveRemovesInvitationsBeforeGroup() {
        // given
        Long actorAccountId = currentAccountId();
        long groupSpaceId = createGroup();
        groupInvitationIssue(groupSpaceId);

        // when
        groupMembershipService.leave(actorAccountId, groupSpaceId);

        // then
        assertThat(invitationRepository.count()).isZero();
        assertThat(memberRepository.count()).isZero();
        assertThat(groupSpaceRepository.existsById(groupSpaceId)).isFalse();
    }

    @Test
    @DisplayName("OWNER도 다른 issuer의 invitation을 조회하거나 폐기할 수 없다")
    void ownerCannotAccessAnotherIssuersInvitation() throws Exception {
        // given
        long groupSpaceId = createGroup();
        var groupSpace = groupSpaceRepository.findById(groupSpaceId).orElseThrow();
        Account otherAccount = accountRepository.saveAndFlush(new Account());
        GroupMember otherIssuer = memberRepository.saveAndFlush(
                new GroupMember(groupSpace, otherAccount.getId(), "other", NOW)
        );
        GroupInvitation invitation = invitationRepository.saveAndFlush(
                new GroupInvitation(
                        groupSpaceId,
                        otherIssuer.getId(),
                        repeatedByte(1),
                        repeatedByte(2),
                        NOW.plus(Duration.ofHours(1))
                )
        );

        // when, then
        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}/invitations", groupSpaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitations", hasSize(0)));
        mockMvc.perform(delete(
                        "/api/group-spaces/{groupSpaceId}/invitations/{invitationId}",
                        groupSpaceId,
                        invitation.getId()
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GROUP_INVITATION_NOT_FOUND"));
        assertThat(invitationRepository.existsById(invitation.getId())).isTrue();
    }

    @Test
    @DisplayName("retention 중 만료 credential은 410이고 cleanup 후에는 404이다")
    void retainedExpiredThenCleanedInvitationHasDistinctErrors() throws Exception {
        // given
        long groupSpaceId = createGroup();
        GroupMember issuer = memberRepository.findByGroupSpaceIdAndAccountIdAndStatus(
                        groupSpaceId,
                        currentAccountId(),
                        GroupMemberStatus.ACTIVE
                )
                .orElseThrow();
        String linkToken = "A".repeat(43);
        GroupInvitation invitation = invitationRepository.saveAndFlush(
                new GroupInvitation(
                        groupSpaceId,
                        issuer.getId(),
                        credentialService.hashValidated(
                                InvitationCredentialType.LINK_TOKEN,
                                linkToken
                        ),
                        repeatedByte(3),
                        NOW.minus(Duration.ofHours(24))
                )
        );

        mockMvc.perform(post("/api/group-invitations/preview")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody("LINK_TOKEN", linkToken)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.errorCode").value("GROUP_INVITATION_EXPIRED"));

        // when
        invitationService.deleteExpiredBatch(NOW.minus(Duration.ofHours(24)));

        // then
        assertThat(invitationRepository.existsById(invitation.getId())).isFalse();
        mockMvc.perform(post("/api/group-invitations/preview")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody("LINK_TOKEN", linkToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GROUP_INVITATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("issuer membership 비활성화 transaction은 issuer invitation을 함께 hard-delete한다")
    void memberDeactivationDeletesIssuerInvitations() {
        // given
        long groupSpaceId = createGroup();
        var groupSpace = groupSpaceRepository.findById(groupSpaceId).orElseThrow();
        Account otherAccount = accountRepository.saveAndFlush(new Account());
        GroupMember issuer = memberRepository.saveAndFlush(
                new GroupMember(groupSpace, otherAccount.getId(), "departing", NOW)
        );
        invitationRepository.saveAndFlush(
                new GroupInvitation(
                        groupSpaceId,
                        issuer.getId(),
                        repeatedByte(4),
                        repeatedByte(5),
                        NOW.plus(Duration.ofHours(1))
                )
        );

        // when
        groupMembershipService.leave(otherAccount.getId(), groupSpaceId);

        // then
        assertThat(invitationRepository.count()).isZero();
        assertThat(memberRepository.findById(issuer.getId()).orElseThrow().getStatus())
                .isEqualTo(GroupMemberStatus.LEFT);
    }

    private void preview(String credential, String credentialType) throws Exception {
        mockMvc.perform(post("/api/group-invitations/preview")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody(credentialType, credential)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.memberCount").value(1));
    }

    private long createGroup() {
        return groupSpaceService.create(
                currentAccountId(),
                new CreateGroupSpaceRequest("Invitation group", null, "issuer")
        ).groupSpaceId();
    }

    private void groupInvitationIssue(long groupSpaceId) {
        try {
            mockMvc.perform(post(
                            "/api/group-spaces/{groupSpaceId}/invitations",
                            groupSpaceId
                    ))
                    .andExpect(status().isCreated());
        } catch (Exception exception) {
            throw new AssertionError("Invitation setup failed.", exception);
        }
    }

    private String issueInviteCode(long groupSpaceId) throws Exception {
        JsonNode issued = read(mockMvc.perform(post(
                        "/api/group-spaces/{groupSpaceId}/invitations",
                        groupSpaceId
                ))
                .andExpect(status().isCreated())
                .andReturn());
        return issued.get("inviteCode").asString();
    }

    private String previewBody(String credentialType, String credential) {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "credentialType", credentialType,
                "credential", credential
        ));
    }

    private byte[] repeatedByte(int value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private JsonNode read(MvcResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedInvitationClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
