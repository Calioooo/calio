package com.calio.calendar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "external.holiday")
public class HolidayApiProperties {

    private static final String DEFAULT_BASE_URL =
            "http://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";

    private String baseUrl = DEFAULT_BASE_URL;
    private String serviceKey;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getServiceKey() {
        return serviceKey;
    }

    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
