package com.calio.calendar.groupinvitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.groupinvitation.config.GroupInvitationProperties;
import com.calio.calendar.groupinvitation.domain.InvitationCredentialType;
import java.security.SecureRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class InvitationCredentialServiceTest {

    @Test
    @DisplayName("credential pair는 256-bit link token과 80-bit Crockford code를 생성한다")
    void generatesCanonicalCredentialPair() {
        // given
        InvitationCredentialService service = service("https://calio.app/invite/");

        // when
        InvitationCredentialPair pair = service.generatePair();

        // then
        assertThat(pair.linkToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(pair.inviteCode()).matches(
                "[0-9ABCDEFGHJKMNPQRSTVWXYZ]{4}(-[0-9ABCDEFGHJKMNPQRSTVWXYZ]{4}){3}"
        );
        assertThat(pair.linkTokenHash()).hasSize(32);
        assertThat(pair.inviteCodeHash()).hasSize(32);
        assertThat(service.inviteUrl(pair.linkToken()))
                .isEqualTo("https://calio.app/invite/" + pair.linkToken());
    }

    @Test
    @DisplayName("CODE는 대소문자와 hyphen을 정규화해 같은 digest로 변환한다")
    void normalizesCodeBeforeHashing() {
        // given
        InvitationCredentialService service = service("https://calio.app/invite");

        // when
        byte[] displayed = service.hashValidated(
                InvitationCredentialType.CODE,
                "0123-abcd-EFgh-jkmn"
        );
        byte[] compact = service.hashValidated(
                InvitationCredentialType.CODE,
                "0123ABCDEFGHJKMN"
        );

        // then
        assertThat(displayed).containsExactly(compact);
    }

    @Test
    @DisplayName("credential 형식 오류는 원문 없이 VALIDATION_FAILED로 변환한다")
    void rejectsInvalidCredentialWithoutEchoingIt() {
        // given
        InvitationCredentialService service = service("https://calio.app/invite");
        String invalidCredential = "secret invalid credential";

        // when, then
        assertThatThrownBy(() -> service.hashValidated(
                InvitationCredentialType.LINK_TOKEN,
                invalidCredential
        ))
                .isInstanceOfSatisfying(CalioException.class, exception -> {
                    assertThat(exception.getErrorCode().name()).isEqualTo("VALIDATION_FAILED");
                    assertThat(exception.getMessage()).doesNotContain(invalidCredential);
                });
    }

    @Test
    @DisplayName("production base URL은 absolute HTTPS이며 query, fragment, placeholder가 없어야 한다")
    void validatesProductionBaseUrl() {
        // given
        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("production");

        // when, then
        assertThatThrownBy(() -> service("http://calio.app/invite", production))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service("https://calio.app/invite?token=x", production))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service("https://calio.app/invite/{token}", production))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("credential DTO의 문자열 표현은 bearer 원문을 노출하지 않는다")
    void redactsCredentialDtoStringRepresentations() {
        // given
        InvitationCredentialPair pair = service("https://calio.app/invite").generatePair();

        // when
        String representation = pair.toString();

        // then
        assertThat(representation)
                .doesNotContain(pair.linkToken())
                .doesNotContain(pair.inviteCode())
                .contains("REDACTED");
    }

    private InvitationCredentialService service(String baseUrl) {
        return service(baseUrl, new MockEnvironment());
    }

    private InvitationCredentialService service(String baseUrl, MockEnvironment environment) {
        GroupInvitationProperties properties = new GroupInvitationProperties();
        properties.setBaseUrl(baseUrl);
        return new InvitationCredentialService(new SecureRandom(), properties, environment);
    }
}
