package com.calio.calendar.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ErrorProblemDetailTest {

    @ParameterizedTest
    @MethodSource("serverErrors")
    @DisplayName("5xx ErrorCode는 HTTP 상태만 유지하고 내부 코드와 메시지를 노출하지 않는다")
    void serverErrorDoesNotExposeInternalCodeOrMessage(ErrorCode errorCode) {
        // when
        ErrorProblemDetail problemDetail = ErrorProblemDetail.from(
                errorCode,
                errorCode.getDefaultMessage()
        );

        // then
        assertThat(problemDetail.type()).isEqualTo("about:blank");
        assertThat(problemDetail.status()).isEqualTo(errorCode.getStatus().value());
        assertThat(problemDetail.title()).isEqualTo(errorCode.getStatus().getReasonPhrase());
        assertThat(problemDetail).isInstanceOf(ErrorProblemDetail.ServerError.class);
    }

    @ParameterizedTest
    @MethodSource("clientErrors")
    @DisplayName("4xx ErrorCode는 클라이언트가 분기할 코드와 메시지를 유지한다")
    void clientErrorExposesContractCodeAndMessage(ErrorCode errorCode) {
        // when
        ErrorProblemDetail problemDetail = ErrorProblemDetail.from(
                errorCode,
                errorCode.getDefaultMessage()
        );

        // then
        assertThat(problemDetail.type()).isEqualTo("about:blank");
        assertThat(problemDetail.status()).isEqualTo(errorCode.getStatus().value());
        assertThat(problemDetail.title()).isEqualTo(errorCode.name());
        assertThat(problemDetail)
                .isInstanceOfSatisfying(ErrorProblemDetail.ClientError.class, clientError -> {
                    assertThat(clientError.detail()).isEqualTo(errorCode.getDefaultMessage());
                    assertThat(clientError.errorCode()).isEqualTo(errorCode.name());
                });
    }

    private static Stream<ErrorCode> serverErrors() {
        return Stream.of(ErrorCode.values())
                .filter(errorCode -> errorCode.getStatus().is5xxServerError());
    }

    private static Stream<ErrorCode> clientErrors() {
        return Stream.of(ErrorCode.values())
                .filter(errorCode -> errorCode.getStatus().is4xxClientError());
    }
}
