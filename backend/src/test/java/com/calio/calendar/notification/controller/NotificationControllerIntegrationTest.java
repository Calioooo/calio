package com.calio.calendar.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.notification.repository.IosPushDeviceRepository;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-notification-test;"
                + "MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IosPushDeviceRepository endpointRepository;

    @Test
    @DisplayName("알림 설정 조회는 서버 기본값과 시간 타입을 직렬화해 반환한다")
    void givenNoSettings_whenGetSettings_thenReturnsDefaultPolicy() throws Exception {
        mockMvc.perform(get("/api/notification-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calendarNotificationsEnabled").value(true))
                .andExpect(jsonPath("$.timedReminderOffset").value("MINUTES_10"))
                .andExpect(jsonPath("$.importantReminderOffset").value("MINUTES_120"))
                .andExpect(jsonPath("$.allDayReminderTime").isString())
                .andExpect(jsonPath("$.dailyBriefingTime").isString());
    }

    @Test
    @DisplayName("지원하지 않는 일정 알림 offset은 validation failure로 거절한다")
    void givenUnsupportedReminderOffset_whenUpdateSettings_thenRejectsRequest() throws Exception {
        mockMvc.perform(put("/api/notification-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsRequest("MINUTES_7", "MINUTES_120")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("지원되는 알림 정책을 수정하면 후속 조회에서 canonical 설정을 반환한다")
    void givenSupportedSettingsUpdate_whenGetSettings_thenReturnsUpdatedPolicy() throws Exception {
        mockMvc.perform(put("/api/notification-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settingsRequest("NONE", "MINUTES_60")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timedReminderOffset").value("NONE"))
                .andExpect(jsonPath("$.importantReminderOffset").value("MINUTES_60"));

        mockMvc.perform(get("/api/notification-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calendarNotificationsEnabled").value(true))
                .andExpect(jsonPath("$.timedReminderOffset").value("NONE"))
                .andExpect(jsonPath("$.importantReminderOffset").value("MINUTES_60"))
                .andExpect(jsonPath("$.allDayReminderTime").value("09:00:00"))
                .andExpect(jsonPath("$.dailyBriefingEnabled").value(false))
                .andExpect(jsonPath("$.dailyBriefingTime").value("08:00:00"));
    }

    @Test
    @DisplayName("iOS endpoint 등록 후 로그아웃 비활성화하면 이후 발송 대상에서 제외된다")
    void givenRegisteredEndpoint_whenDeactivate_thenRemovesEligibleEndpoint() throws Exception {
        mockMvc.perform(put("/api/notification-endpoints/ios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "installationId": "iphone-installation",
                                  "apnsToken": "device-token",
                                  "authorizationStatus": "AUTHORIZED"
                                }
                                """))
                .andExpect(status().isNoContent());

        assertThat(endpointRepository.findByApnsToken("device-token"))
                .get()
                .extracting(endpoint -> endpoint.isEligible())
                .isEqualTo(true);

        mockMvc.perform(delete("/api/notification-endpoints/ios/{installationId}", "iphone-installation"))
                .andExpect(status().isNoContent());

        assertThat(endpointRepository.findByApnsToken("device-token"))
                .get()
                .extracting(endpoint -> endpoint.isEligible())
                .isEqualTo(false);
    }

    @Test
    @DisplayName("새 설치본이 이미 다른 설치본에 연결된 APNs 토큰을 등록하면 기존 endpoint를 비활성화하고 토큰을 해제한다")
    void givenTokenOwnedByAnotherInstallation_whenRegister_thenTransfersTokenOwnership() throws Exception {
        mockMvc.perform(put("/api/notification-endpoints/ios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(endpointRequest("first-installation", "transferred-device-token")))
                .andExpect(status().isNoContent());

        Long previousEndpointId = endpointRepository.findByApnsToken("transferred-device-token")
                .orElseThrow()
                .getId();

        mockMvc.perform(put("/api/notification-endpoints/ios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(endpointRequest("second-installation", "transferred-device-token")))
                .andExpect(status().isNoContent());

        assertThat(endpointRepository.findById(previousEndpointId))
                .get()
                .satisfies(endpoint -> {
                    assertThat(endpoint.getApnsToken()).isNull();
                    assertThat(endpoint.isEligible()).isFalse();
                });
        assertThat(endpointRepository.findByApnsToken("transferred-device-token"))
                .get()
                .extracting(endpoint -> endpoint.isEligible())
                .isEqualTo(true);
    }

    private String settingsRequest(String timedReminderOffset, String importantReminderOffset) {
        return """
                {
                  "calendarNotificationsEnabled": true,
                  "timedReminderOffset": "%s",
                  "importantReminderOffset": "%s",
                  "allDayReminderTime": "09:00:00",
                  "dailyBriefingEnabled": false,
                  "dailyBriefingTime": "08:00:00"
                }
                """.formatted(timedReminderOffset, importantReminderOffset);
    }

    private String endpointRequest(String installationId, String apnsToken) {
        return """
                {
                  "installationId": "%s",
                  "apnsToken": "%s",
                  "authorizationStatus": "AUTHORIZED"
                }
                """.formatted(installationId, apnsToken);
    }
}
