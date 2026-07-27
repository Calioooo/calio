package com.calio.calendar.groupspace.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.domain.InvitationCredentialType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class InvitationCredentialCodec {

    private static final char[] CROCKFORD_ALPHABET =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final Pattern LINK_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final Pattern CODE_PATTERN = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{16}$");
    private static final int LINK_TOKEN_BYTES = 32;
    private static final int CODE_BYTES = 10;

    private final SecureRandom secureRandom;

    public InvitationCredentialCodec(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public CredentialPair generate() {
        byte[] linkBytes = randomBytes(LINK_TOKEN_BYTES);
        byte[] codeBytes = randomBytes(CODE_BYTES);
        String linkToken = Base64.getUrlEncoder().withoutPadding().encodeToString(linkBytes);
        String canonicalCode = encodeCrockford(codeBytes);
        return new CredentialPair(
                linkToken,
                formatCode(canonicalCode),
                digest(linkToken),
                digest(canonicalCode)
        );
    }

    public byte[] digest(InvitationCredentialType type, String rawCredential) {
        return digest(canonicalize(type, rawCredential));
    }

    public String canonicalize(InvitationCredentialType type, String rawCredential) {
        if (type == null || rawCredential == null || rawCredential.isBlank()) {
            throw validationFailed();
        }
        return switch (type) {
            case LINK_TOKEN -> canonicalizeLinkToken(rawCredential);
            case CODE -> canonicalizeCode(rawCredential);
        };
    }

    private String canonicalizeLinkToken(String rawCredential) {
        if (!LINK_TOKEN_PATTERN.matcher(rawCredential).matches()) {
            throw validationFailed();
        }
        return rawCredential;
    }

    private String canonicalizeCode(String rawCredential) {
        String canonical = rawCredential.replace("-", "").toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(canonical).matches()) {
            throw validationFailed();
        }
        return canonical;
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private String encodeCrockford(byte[] bytes) {
        StringBuilder encoded = new StringBuilder(16);
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                encoded.append(CROCKFORD_ALPHABET[(buffer >> bits) & 31]);
            }
        }
        return encoded.toString();
    }

    private String formatCode(String canonicalCode) {
        return canonicalCode.substring(0, 4) + "-"
                + canonicalCode.substring(4, 8) + "-"
                + canonicalCode.substring(8, 12) + "-"
                + canonicalCode.substring(12, 16);
    }

    private byte[] digest(String canonicalCredential) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(canonicalCredential.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private CalioException validationFailed() {
        return new CalioException(ErrorCode.VALIDATION_FAILED);
    }

    public record CredentialPair(
            String linkToken,
            String inviteCode,
            byte[] linkTokenHash,
            byte[] inviteCodeHash
    ) {
    }
}
