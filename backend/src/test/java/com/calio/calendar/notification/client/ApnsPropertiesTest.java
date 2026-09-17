package com.calio.calendar.notification.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ApnsPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(ApnsPropertiesConfiguration.class);

  @Test
  @DisplayName("APNs 환경값이 없으면 애플리케이션 설정을 생성하지 않는다")
  void givenMissingEnvironment_whenBindingProperties_thenContextFails() {
    contextRunner.run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("지원하지 않는 APNs 환경값이면 애플리케이션 설정을 생성하지 않는다")
  void givenUnsupportedEnvironment_whenBindingProperties_thenContextFails() {
    contextRunner
        .withPropertyValues("notifications.apns.environment=unsupported")
        .run(context -> assertThat(context).hasFailed());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(ApnsProperties.class)
  static class ApnsPropertiesConfiguration {}
}
