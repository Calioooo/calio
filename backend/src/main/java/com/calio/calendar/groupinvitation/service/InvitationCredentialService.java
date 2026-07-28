package com.calio.calendar.groupinvitation.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.config.GroupInvitationProperties;
import com.calio.calendar.groupinvitation.domain.InvitationCredentialType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class InvitationCredentialService {

    private static final int LINK_TOKEN_BYTES = 32;
    private static final int INVITE_CODE_BYTES = 10;
    private static final String CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final Pattern LINK_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern INVITE_CODE_PATTERN =
            Pattern.compile("[0-9ABCDEFGHJKMNPQRSTVWXYZ]{16}");
    private static final Set<String> TOKEN_PLACEHOLDERS =
            Set.of("{token}", "{linkToken}", ":token", "${token}");

    private final SecureRandom secureRandom;
    private final URI baseUri;

    public InvitationCredentialService(
            SecureRandom secureRandom,
            GroupInvitationProperties properties,
            Environment environment
    ) {
        this.secureRandom = secureRandom;
        this.baseUri = validateBaseUri(
                properties.getBaseUrl(),
                environment.acceptsProfiles(Profiles.of("prod", "production"))
        );
    }

    InvitationCredentialPair generatePair() {
        String linkToken = generateLinkToken();
        String inviteCode = generateInviteCode();
        return new InvitationCredentialPair(
                linkToken,
                inviteCode,
                hash(linkToken),
                hash(normalizeCode(inviteCode))
        );
    }

    public byte[] hashValidated(InvitationCredentialType credentialType, String credential) {
        return switch (credentialType) {
            case LINK_TOKEN -> hash(validateLinkToken(credential));
            case CODE -> hash(normalizeCode(credential));
        };
    }

    String inviteUrl(String linkToken) {
        String canonicalToken = validateLinkToken(linkToken);
        return UriComponentsBuilder.fromUri(baseUri)
                .pathSegment(canonicalToken)
                .build()
                .encode()
                .toUriString();
    }

    private String generateLinkToken() {
        byte[] bytes = randomBytes(LINK_TOKEN_BYTES);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateInviteCode() {
        byte[] bytes = randomBytes(INVITE_CODE_BYTES);
        StringBuilder normalized = new StringBuilder(16);
        int bitBuffer = 0;
        int bitsInBuffer = 0;

        for (byte value : bytes) {
            bitBuffer = (bitBuffer << 8) | (value & 0xff);
            bitsInBuffer += 8;
            while (bitsInBuffer >= 5) {
                bitsInBuffer -= 5;
                normalized.append(CROCKFORD_ALPHABET.charAt((bitBuffer >> bitsInBuffer) & 31));
            }
        }
        return normalized.substring(0, 4)
                + "-" + normalized.substring(4, 8)
                + "-" + normalized.substring(8, 12)
                + "-" + normalized.substring(12, 16);
    }

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private String validateLinkToken(String credential) {
        if (credential == null || !LINK_TOKEN_PATTERN.matcher(credential).matches()) {
            throw validationFailed();
        }
        return credential;
    }

    private String normalizeCode(String credential) {
        if (credential == null) {
            throw validationFailed();
        }

        String normalized = credential
                .toUpperCase(Locale.ROOT)
                .replace("-", "");
        if (!INVITE_CODE_PATTERN.matcher(normalized).matches()) {
            throw validationFailed();
        }
        return normalized;
    }

    private byte[] hash(String canonicalCredential) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(canonicalCredential.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required credential digest is unavailable.", exception);
        }
    }

    private URI validateBaseUri(String rawBaseUrl, boolean production) {
        URI candidate;
        try {
            candidate = URI.create(rawBaseUrl);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid group invitation base URL.", exception);
        }

        if (!candidate.isAbsolute()
                || candidate.getHost() == null
                || candidate.getRawQuery() != null
                || candidate.getRawFragment() != null
                || containsPlaceholder(rawBaseUrl)
                || (production && !"https".equalsIgnoreCase(candidate.getScheme()))) {
            throw new IllegalStateException("Invalid group invitation base URL.");
        }

        String normalized = rawBaseUrl.replaceAll("/+$", "");
        return URI.create(normalized);
    }

    private boolean containsPlaceholder(String rawBaseUrl) {
        return TOKEN_PLACEHOLDERS.stream().anyMatch(rawBaseUrl::contains);
    }

    private CalioException validationFailed() {
        return new CalioException(ErrorCode.VALIDATION_FAILED);
    }
}
