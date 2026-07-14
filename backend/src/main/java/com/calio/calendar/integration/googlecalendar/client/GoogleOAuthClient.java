package com.calio.calendar.integration.googlecalendar.client;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.googlecalendar.config.GoogleOAuthProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GoogleOAuthClient {

    private final GoogleOAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public GoogleOAuthClient(GoogleOAuthProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, RestClient.builder().build());
    }

    GoogleOAuthClient(GoogleOAuthProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    public GoogleTokenResponse exchangeAuthorizationCode(String authorizationCode) {
        String responseBody = requestToken(authorizationCode);
        return parseTokenResponse(responseBody);
    }

    public GoogleUserInfoResponse fetchUserInfo(String accessToken) {
        String responseBody = requestUserInfo(accessToken);
        return parseUserInfoResponse(responseBody);
    }

    private String requestToken(String authorizationCode) {
        try {
            return restClient.post()
                    .uri(properties.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(tokenRequestBody(authorizationCode))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException exception) {
            throw new CalioException(ErrorCode.GOOGLE_OAUTH_TOKEN_EXCHANGE_FAILED, exception);
        }
    }

    private MultiValueMap<String, String> tokenRequestBody(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", authorizationCode);
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("redirect_uri", properties.getRedirectUri());
        form.add("grant_type", "authorization_code");
        return form;
    }

    private GoogleTokenResponse parseTokenResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String accessToken = textOrNull(root.get("access_token"));
            String refreshToken = textOrNull(root.get("refresh_token"));
            String tokenType = textOrNull(root.get("token_type"));
            long expiresIn = root.path("expires_in").asLong(0);

            if (!isValidTokenResponse(accessToken, refreshToken, expiresIn, tokenType)) {
                throw new CalioException(ErrorCode.GOOGLE_OAUTH_INVALID_TOKEN_RESPONSE);
            }

            return new GoogleTokenResponse(accessToken, refreshToken, expiresIn);
        } catch (JacksonException exception) {
            throw new CalioException(ErrorCode.GOOGLE_OAUTH_INVALID_TOKEN_RESPONSE, exception);
        }
    }

    private boolean isValidTokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            String tokenType
    ) {
        return hasText(accessToken)
                && hasText(refreshToken)
                && expiresIn > 0
                && (tokenType == null || "Bearer".equalsIgnoreCase(tokenType));
    }

    private String requestUserInfo(String accessToken) {
        try {
            return restClient.get()
                    .uri(properties.getUserInfoUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException exception) {
            throw new CalioException(ErrorCode.GOOGLE_OAUTH_USERINFO_FAILED, exception);
        }
    }

    private GoogleUserInfoResponse parseUserInfoResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String subject = textOrNull(root.get("sub"));
            String email = textOrNull(root.get("email"));
            Boolean emailVerified = booleanOrNull(root.get("email_verified"));

            if (!hasText(subject) || !hasText(email) || Boolean.FALSE.equals(emailVerified)) {
                throw new CalioException(ErrorCode.GOOGLE_OAUTH_INVALID_USERINFO_RESPONSE);
            }

            return new GoogleUserInfoResponse(subject, email);
        } catch (JacksonException exception) {
            throw new CalioException(ErrorCode.GOOGLE_OAUTH_INVALID_USERINFO_RESPONSE, exception);
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asString();
    }

    private Boolean booleanOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.asBoolean();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
