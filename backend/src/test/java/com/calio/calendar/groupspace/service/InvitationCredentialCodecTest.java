package com.calio.calendar.groupspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.domain.InvitationCredentialType;
import java.security.SecureRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InvitationCredentialCodecTest {

    private final InvitationCredentialCodec codec = new InvitationCredentialCodec(new SecureRandom());

    @Test
    @DisplayName("생성 credential은 43자 Base64URL token과 16자 Crockford 표시 code를 제공한다")
    void generatedCredentialsUseCanonicalFormats() {
        InvitationCredentialCodec.CredentialPair credentials = codec.generate();

        assertThat(credentials.linkToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(credentials.inviteCode()).matches(
                "[0-9A-HJKMNP-TV-Z]{4}(?:-[0-9A-HJKMNP-TV-Z]{4}){3}"
        );
        assertThat(credentials.linkTokenHash()).hasSize(32);
        assertThat(credentials.inviteCodeHash()).hasSize(32);
    }

    @Test
    @DisplayName("표시 code와 hyphen 없는 code는 같은 SHA-256 digest로 canonicalize된다")
    void displayedAndCompactCodesHaveSameDigest() {
        InvitationCredentialCodec.CredentialPair credentials = codec.generate();
        byte[] displayed = codec.digest(InvitationCredentialType.CODE, credentials.inviteCode());
        byte[] compact = codec.digest(
                InvitationCredentialType.CODE,
                credentials.inviteCode().replace("-", "")
        );

        assertThat(displayed).isEqualTo(compact);
        assertThat(displayed).isEqualTo(credentials.inviteCodeHash());
    }

    @Test
    @DisplayName("credential type과 맞지 않는 형식은 VALIDATION_FAILED다")
    void invalidCredentialFormatIsValidationFailure() {
        assertThatThrownBy(() -> codec.digest(InvitationCredentialType.LINK_TOKEN, "short"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED)
                );
    }
}
