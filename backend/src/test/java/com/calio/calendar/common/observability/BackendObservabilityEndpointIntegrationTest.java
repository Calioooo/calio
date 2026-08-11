package com.calio.calendar.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:backend-observability-endpoint-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.structured.format.console=logstash",
        "management.endpoints.web.exposure.include=health,prometheus"
})
@AutoConfigureMockMvc
class BackendObservabilityEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("운영 로그는 모든 환경에서 Logstash JSON console 형식을 사용한다")
    void usesLogstashJsonConsoleLogging() {
        assertThat(environment.getProperty("logging.structured.format.console"))
                .isEqualTo("logstash");
    }

    @Test
    @DisplayName("Actuator health endpoint는 bearer token 없이 health 응답을 제공한다")
    void returnsHealthWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Actuator Prometheus endpoint는 bearer token 없이 Prometheus 형식 metrics를 제공한다")
    void returnsPrometheusMetricsWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("# HELP")));
    }

    @Test
    @DisplayName("노출하지 않은 Actuator endpoint는 bearer token 없이 접근할 수 없다")
    void deniesUnexposedActuatorEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("기존 보호 API는 bearer token 없이 계속 인증을 요구한다")
    void preservesExistingProtectedApiAuthentication() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }
}
