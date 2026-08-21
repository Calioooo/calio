package com.calio.calendar.holiday.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.holiday.repository.NationalHolidayRepository;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import com.calio.calendar.tag.repository.TagRepository;
import com.calio.calendar.holiday.domain.NationalHoliday;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:national-holiday-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class NationalHolidayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NationalHolidayRepository nationalHolidayRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TagRepository tagRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        nationalHolidayRepository.deleteAll();
        tagRepository.deleteAll();
        tagRepository.save(new Tag(TagType.PERSONAL_DEFAULT, "기타", "#64748B"));
    }

    @Test
    @DisplayName("사용자는 날짜 범위에 포함된 공휴일을 날짜와 제목 오름차순으로 조회한다")
    void givenNationalHolidaysAcrossRange_whenListNationalHolidays_thenReturnsInclusiveRangeSortedByDateAndTitle()
            throws Exception {
        // given
        nationalHolidayRepository.save(new NationalHoliday(LocalDate.of(2026, 5, 4), "Before"));
        NationalHoliday childrensDay = nationalHolidayRepository.save(
                new NationalHoliday(LocalDate.of(2026, 5, 5), "Children's Day")
        );
        NationalHoliday alternateHoliday = nationalHolidayRepository.save(
                new NationalHoliday(LocalDate.of(2026, 5, 5), "Alternate Holiday")
        );
        NationalHoliday memorialDay = nationalHolidayRepository.save(
                new NationalHoliday(LocalDate.of(2026, 6, 6), "Memorial Day")
        );
        nationalHolidayRepository.save(new NationalHoliday(LocalDate.of(2026, 6, 7), "After"));

        // when
        mockMvc.perform(get("/api/national-holidays")
                        .param("from", "2026-05-05")
                        .param("to", "2026-06-06"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].nationalHolidayId").value(alternateHoliday.getNationalHolidayId()))
                .andExpect(jsonPath("$[0].holidayDate").value("2026-05-05"))
                .andExpect(jsonPath("$[0].holidayTitle").value("Alternate Holiday"))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[1].nationalHolidayId").value(childrensDay.getNationalHolidayId()))
                .andExpect(jsonPath("$[1].holidayDate").value("2026-05-05"))
                .andExpect(jsonPath("$[1].holidayTitle").value("Children's Day"))
                .andExpect(jsonPath("$[2].nationalHolidayId").value(memorialDay.getNationalHolidayId()))
                .andExpect(jsonPath("$[2].holidayDate").value("2026-06-06"))
                .andExpect(jsonPath("$[2].holidayTitle").value("Memorial Day"));
    }

    @Test
    @DisplayName("사용자는 from과 to가 같은 날짜이면 해당 날짜의 공휴일만 조회한다")
    void givenSameFromAndTo_whenListNationalHolidays_thenReturnsOnlyThatDateHolidays() throws Exception {
        // given
        nationalHolidayRepository.save(new NationalHoliday(LocalDate.of(2026, 5, 4), "Before"));
        NationalHoliday childrensDay = nationalHolidayRepository.save(
                new NationalHoliday(LocalDate.of(2026, 5, 5), "Children's Day")
        );
        nationalHolidayRepository.save(new NationalHoliday(LocalDate.of(2026, 5, 6), "After"));

        // when
        mockMvc.perform(get("/api/national-holidays")
                        .param("from", "2026-05-05")
                        .param("to", "2026-05-05"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nationalHolidayId").value(childrensDay.getNationalHolidayId()))
                .andExpect(jsonPath("$[0].holidayDate").value("2026-05-05"))
                .andExpect(jsonPath("$[0].holidayTitle").value("Children's Day"));
    }

    @Test
    @DisplayName("사용자는 from이 to보다 늦은 공휴일 조회를 요청할 수 없다")
    void givenFromAfterTo_whenListNationalHolidays_thenReturnsInvalidTimeRange() throws Exception {
        // when
        mockMvc.perform(get("/api/national-holidays")
                        .param("from", "2026-06-06")
                        .param("to", "2026-05-05"))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("INVALID_TIME_RANGE"))
                .andExpect(jsonPath("$.detail").value("Invalid time range."))
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("사용자는 필수 날짜 파라미터 없이 공휴일을 조회할 수 없다")
    void givenMissingDateParameter_whenListNationalHolidays_thenReturnsValidationFailed() throws Exception {
        // when, then
        mockMvc.perform(get("/api/national-holidays")
                        .param("to", "2026-05-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Validation failed."))
                .andExpect(jsonPath("$.*", hasSize(6)));

        mockMvc.perform(get("/api/national-holidays")
                        .param("from", "2026-05-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Validation failed."))
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("사용자는 yyyy-MM-dd가 아닌 날짜 형식으로 공휴일을 조회할 수 없다")
    void givenInvalidDateFormat_whenListNationalHolidays_thenReturnsValidationFailed() throws Exception {
        // when
        mockMvc.perform(get("/api/national-holidays")
                        .param("from", "2026/05/05")
                        .param("to", "2026-06-06"))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Validation failed."))
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("기존 일정 조회 API는 공휴일을 섞지 않고 일정 응답 계약만 반환한다")
    void givenNationalHolidayAndEvent_whenListEvents_thenReturnsOnlyEventResponseShape() throws Exception {
        // given
        nationalHolidayRepository.save(new NationalHoliday(LocalDate.of(2026, 5, 5), "Children's Day"));
        createEvent("Planning", "2026-05-05T00:00:00Z", "2026-05-05T01:00:00Z");

        // when
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-05-05T00:00:00Z")
                        .param("to", "2026-05-05T01:00:00Z"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].title").value("Planning"))
                .andExpect(jsonPath("$[0].startAt").value("2026-05-05T00:00:00Z"))
                .andExpect(jsonPath("$[0].endAt").value("2026-05-05T01:00:00Z"))
                .andExpect(jsonPath("$[0].importantEvent").value(false))
                .andExpect(jsonPath("$[0].tag.title").value("기타"))
                .andExpect(jsonPath("$[0].nationalHolidayId").doesNotExist())
                .andExpect(jsonPath("$[0].holidayDate").doesNotExist())
                .andExpect(jsonPath("$[0].holidayTitle").doesNotExist());
    }

    private void createEvent(String title, String startAt, String endAt) throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "startAt": "%s",
                                  "endAt": "%s",
                                  "allDay": false,
                                  "timeZone": "UTC"
                                }
                                """.formatted(title, startAt, endAt)))
                .andExpect(status().isCreated());
    }
}
