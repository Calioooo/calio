package com.calio.calendar.aicalendar.config;

import com.openai.core.Timeout;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CalendarAiOpenAiConfiguration {

    @Bean
    OpenAiHttpClientBuilderCustomizer calendarAiOpenAiHttpClientBuilderCustomizer(
            CalendarAIProperties properties
    ) {
        return builder -> builder.timeout(Timeout.builder()
                .connect(properties.getConnectionTimeout())
                .read(properties.getRunTimeout())
                .write(properties.getRunTimeout())
                .request(properties.getRunTimeout())
                .build());
    }
}
