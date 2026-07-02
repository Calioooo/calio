package com.calio.calendar.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.repository.NationalHolidayRepository;
import com.calio.calendar.repository.entity.NationalHoliday;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:national-holiday-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class NationalHolidayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NationalHolidayRepository nationalHolidayRepository;

    @BeforeEach
    void setUp() {
        nationalHolidayRepository.deleteAll();
    }

    @Test
    @DisplayName("사용자는 날짜 범위의 공휴일을 holidayDate, holidayTitle 오름차순으로 조회한다")
    void givenNationalHolidaysInRange_whenListNationalHolidays_thenReturnsSortedHolidays()
            throws Exception {
        // given
        nationalHolidayRepository.save(new NationalHoliday(LocalDate.parse("2026-01-02"), "Beta"));
        nationalHolidayRepository.save(new NationalHoliday(LocalDate.parse("2026-01-01"), "Zoo"));
        nationalHolidayRepository.save(new NationalHoliday(LocalDate.parse("2026-01-01"), "Alpha"));
        nationalHolidayRepository.save(new NationalHoliday(LocalDate.parse("2026-01-03"), "Outside"));

        // when
        mockMvc.perform(get("/api/national-holidays")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-02"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].nationalHolidayId").isNumber())
                .andExpect(jsonPath("$[0].holidayDate").value("2026-01-01"))
                .andExpect(jsonPath("$[0].holidayTitle").value("Alpha"))
                .andExpect(jsonPath("$[1].holidayDate").value("2026-01-01"))
                .andExpect(jsonPath("$[1].holidayTitle").value("Zoo"))
                .andExpect(jsonPath("$[2].holidayDate").value("2026-01-02"))
                .andExpect(jsonPath("$[2].holidayTitle").value("Beta"));
    }

    @Test
    @DisplayName("사용자는 from이 to보다 늦은 공휴일 범위를 조회할 수 없다")
    void givenFromAfterTo_whenListNationalHolidays_thenReturnsInvalidTimeRange() throws Exception {
        // when
        mockMvc.perform(get("/api/national-holidays")
                        .param("from", "2026-01-02")
                        .param("to", "2026-01-01"))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIME_RANGE"))
                .andExpect(jsonPath("$.message").value("Invalid time range."))
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("사용자는 필수 공휴일 조회 파라미터 없이 조회할 수 없다")
    void givenMissingRequiredParameter_whenListNationalHolidays_thenReturnsValidationFailed()
            throws Exception {
        // when, then
        mockMvc.perform(get("/api/national-holidays")
                        .param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.*", hasSize(2)));

        mockMvc.perform(get("/api/national-holidays")
                        .param("from", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("사용자는 ISO date 형식이 아닌 날짜로 공휴일을 조회할 수 없다")
    void givenMalformedDateParameter_whenListNationalHolidays_thenReturnsValidationFailed()
            throws Exception {
        // when
        mockMvc.perform(get("/api/national-holidays")
                        .param("from", "not-a-date")
                        .param("to", "2026-01-01"))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.*", hasSize(2)));
    }
}
