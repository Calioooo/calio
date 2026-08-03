package com.calio.calendar.external.google;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.dto.GoogleTokenResponse;
import com.calio.calendar.external.google.dto.GoogleAccessTokenRefreshResponse;
import com.calio.calendar.external.google.dto.GoogleUserInfoResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GoogleOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
    private static final String AUTHORIZATION_CODE_GRANT = "authorization_code";
    private static final String REFRESH_TOKEN_GRANT = "refresh_token";
    private static final String INVALID_TOKEN_ERROR = "invalid_token";
    private static final String INVALID_GRANT_ERROR = "invalid_grant";

    private final GoogleOAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public GoogleOAuthClient(
            GoogleOAuthProperties properties,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder
    ) {
        this(properties, objectMapper, createRestClient(restClientBuilder));
    }

    GoogleOAuthClient(
            GoogleOAuthProperties properties,
            ObjectMapper objectMapper,
            RestClient restClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    public GoogleTokenResponse exchangeAuthorizationCode(String authorizationCode) {
        try {
            GoogleTokenResponse response = restClient.post()
                    .uri(properties.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(tokenExchangeForm(authorizationCode))
                    .retrieve()
                    .body(GoogleTokenResponse.class);
            if (response == null) {
                throw new CalioException(ErrorCode.GOOGLE_TOKEN_RESPONSE_INVALID);
            }
            return response;
        } catch (CalioException exception) {
            logGoogleApiFailure("tokenExchange", exception.getErrorCode(), exception);
            throw exception;
        } catch (RestClientException exception) {
            ErrorCode errorCode = isDeserializationFailure(exception)
                    ? ErrorCode.GOOGLE_TOKEN_RESPONSE_INVALID
                    : ErrorCode.GOOGLE_TOKEN_EXCHANGE_FAILED;
            logGoogleApiFailure("tokenExchange", errorCode, exception);
            throw new CalioException(errorCode, exception);
        }
    }

    public GoogleUserInfoResponse fetchUserInfo(String accessToken) {
        try {
            GoogleUserInfoResponse response = restClient.get()
                    .uri(properties.getUserInfoUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GoogleUserInfoResponse.class);
            if (response == null) {
                throw new CalioException(ErrorCode.GOOGLE_USER_INFO_INVALID);
            }
            return response;
        } catch (CalioException exception) {
            logGoogleApiFailure("userInfoFetch", exception.getErrorCode(), exception);
            throw exception;
        } catch (RestClientException exception) {
            ErrorCode errorCode = isDeserializationFailure(exception)
                    ? ErrorCode.GOOGLE_USER_INFO_INVALID
                    : ErrorCode.GOOGLE_USER_INFO_FETCH_FAILED;
            logGoogleApiFailure("userInfoFetch", errorCode, exception);
            throw new CalioException(errorCode, exception);
        }
    }

    public GoogleAccessTokenRefreshResponse refreshAccessToken(String refreshToken) {
        try {
            GoogleAccessTokenRefreshResponse response = restClient.post()
                    .uri(properties.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(accessTokenRefreshForm(refreshToken))
                    .retrieve()
                    .body(GoogleAccessTokenRefreshResponse.class);
            if (response == null) {
                throw new CalioException(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED);
            }
            return response;
        } catch (CalioException exception) {
            logGoogleApiFailure("accessTokenRefresh", exception.getErrorCode(), exception);
            throw exception;
        } catch (RestClientResponseException exception) {
            if (isInvalidGrantResponse(exception)) {
                logGoogleApiFailure(
                        "accessTokenRefresh",
                        ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED,
                        exception
                );
                throw new GoogleOAuthInvalidGrantException(exception);
            }
            ErrorCode errorCode = ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED;
            logGoogleApiFailure("accessTokenRefresh", errorCode, exception);
            throw new CalioException(errorCode, exception);
        } catch (RestClientException exception) {
            ErrorCode errorCode = isDeserializationFailure(exception)
                    ? ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED
                    : ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED;
            logGoogleApiFailure("accessTokenRefresh", errorCode, exception);
            throw new CalioException(errorCode, exception);
        }
    }

    public boolean revokeToken(String token) {
        try {
            restClient.post()
                    .uri(properties.getRevokeUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(tokenRevokeForm(token))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException exception) {
            if (isInvalidTokenRevokeResponse(exception)) {
                logInvalidTokenRevoke(exception);
                return false;
            }
            logGoogleApiFailure("tokenRevoke", ErrorCode.GOOGLE_TOKEN_REVOKE_FAILED, exception);
            throw new CalioException(ErrorCode.GOOGLE_TOKEN_REVOKE_FAILED, exception);
        } catch (RestClientException exception) {
            logGoogleApiFailure("tokenRevoke", ErrorCode.GOOGLE_TOKEN_REVOKE_FAILED, exception);
            throw new CalioException(ErrorCode.GOOGLE_TOKEN_REVOKE_FAILED, exception);
        }
    }

    private void logGoogleApiFailure(String operation, ErrorCode errorCode, Exception exception) {
        log.warn(
                "Google OAuth client failure. operation={} errorCode={} causeType={} httpStatus={}",
                operation,
                errorCode.name(),
                exception.getClass().getSimpleName(),
                httpStatusOrNull(exception)
        );
    }

    private void logInvalidTokenRevoke(RestClientResponseException exception) {
        log.warn(
                "Google OAuth token revoke returned invalid token. operation={} causeType={} httpStatus={}",
                "tokenRevoke",
                exception.getClass().getSimpleName(),
                httpStatusOrNull(exception)
        );
    }

    private Integer httpStatusOrNull(Exception exception) {
        if (exception instanceof RestClientResponseException responseException) {
            HttpStatusCode statusCode = responseException.getStatusCode();
            return statusCode.value();
        }
        return null;
    }

    private MultiValueMap<String, String> tokenExchangeForm(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", authorizationCode);
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("redirect_uri", properties.getRedirectUri());
        form.add("grant_type", AUTHORIZATION_CODE_GRANT);
        return form;
    }

    private MultiValueMap<String, String> tokenRevokeForm(String token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        return form;
    }

    private MultiValueMap<String, String> accessTokenRefreshForm(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("grant_type", REFRESH_TOKEN_GRANT);
        return form;
    }

    private boolean isDeserializationFailure(RestClientException exception) {
        return exception.contains(HttpMessageNotReadableException.class);
    }

    private boolean isInvalidTokenRevokeResponse(RestClientResponseException exception) {
        try {
            JsonNode root = objectMapper.readTree(exception.getResponseBodyAsString());
            JsonNode error = root.get("error");
            return error != null && INVALID_TOKEN_ERROR.equals(error.asString());
        } catch (JacksonException ignored) {
            return false;
        }
    }

    private boolean isInvalidGrantResponse(RestClientResponseException exception) {
        try {
            JsonNode root = objectMapper.readTree(exception.getResponseBodyAsString());
            JsonNode error = root.get("error");
            return error != null && INVALID_GRANT_ERROR.equals(error.asString());
        } catch (JacksonException ignored) {
            return false;
        }
    }

    private static RestClient createRestClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder
                .requestFactory(createRequestFactory())
                .build();
    }

    private static SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return requestFactory;
    }

}
