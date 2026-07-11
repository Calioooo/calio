package com.calio.calendar.controller;

import static org.hamcrest.Matchers.hasSize;
import static com.calio.calendar.security.TestAccountSupport.currentAccountReference;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.repository.TagRepository;
import com.calio.calendar.repository.entity.Tag;
import com.calio.calendar.repository.entity.TagType;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-tag-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class TagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TagRepository tagRepository;

    @BeforeEach
    void setUp() {
        tagRepository.deleteAll();
    }

    @Test
    @DisplayName("사용자는 DEFAULT와 CUSTOM 태그 목록을 id, title, colorCode, tagType으로 조회한다")
    void givenDefaultAndCustomTags_whenListTags_thenReturnsAllTags() throws Exception {
        // given
        tagRepository.save(new Tag(TagType.DEFAULT, "업무", "#2563eb"));
        tagRepository.save(new Tag(TagType.DEFAULT, "기타", "#64748b"));
        tagRepository.save(new Tag(TagType.CUSTOM, "사용자", "#111111", currentAccountReference()));

        // when
        mockMvc.perform(get("/api/tags"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].title").value("업무"))
                .andExpect(jsonPath("$[0].colorCode").value("#2563EB"))
                .andExpect(jsonPath("$[0].tagType").value("DEFAULT"))
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist())
                .andExpect(jsonPath("$[1].title").value("기타"))
                .andExpect(jsonPath("$[1].colorCode").value("#64748B"))
                .andExpect(jsonPath("$[1].tagType").value("DEFAULT"))
                .andExpect(jsonPath("$[2].title").value("사용자"))
                .andExpect(jsonPath("$[2].colorCode").value("#111111"))
                .andExpect(jsonPath("$[2].tagType").value("CUSTOM"));
    }
}
