package com.calio.calendar.external.google;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.dto.GoogleTokenResponse;
import com.calio.calendar.external.google.dto.GoogleUserInfoResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class GoogleOAuthClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
    private static final String AUTHORIZATION_CODE_GRANT = "authorization_code";

    private final GoogleOAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public GoogleOAuthClient(GoogleOAuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .requestFactory(createRequestFactory())
                .build();
    }

    GoogleOAuthClient(
            GoogleOAuthProperties properties,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    public GoogleTokenResponse exchangeAuthorizationCode(String authorizationCode) {
        try {
            String responseBody = restClient.post()
                    .uri(properties.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(tokenExchangeForm(authorizationCode))
                    .retrieve()
                    .body(String.class);
            return GoogleTokenResponse.fromJson(responseBody, objectMapper);
        } catch (CalioException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new CalioException(ErrorCode.GOOGLE_TOKEN_RESPONSE_INVALID, exception);
        } catch (RestClientException exception) {
            throw new CalioException(ErrorCode.GOOGLE_TOKEN_EXCHANGE_FAILED, exception);
        }
    }

    public GoogleUserInfoResponse fetchUserInfo(String accessToken) {
        try {
            String responseBody = restClient.get()
                    .uri(properties.getUserInfoUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            return GoogleUserInfoResponse.fromJson(responseBody, objectMapper);
        } catch (CalioException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw new CalioException(ErrorCode.GOOGLE_USER_INFO_INVALID, exception);
        } catch (RestClientException exception) {
            throw new CalioException(ErrorCode.GOOGLE_USER_INFO_FETCH_FAILED, exception);
        }
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

    private SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return requestFactory;
    }
}
