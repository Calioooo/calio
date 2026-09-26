package com.calio.calendar.tag.controller;

import static com.calio.calendar.security.TestAccountSupport.currentAccountId;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:calendar-group-tag-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class GroupTagControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private TagRepository tagRepository;

  @Autowired private GroupMemberRepository groupMemberRepository;

  @Autowired private GroupSpaceRepository groupSpaceRepository;

  @BeforeEach
  void setUp() {
    tagRepository.deleteAll();
    groupMemberRepository.deleteAll();
    groupSpaceRepository.deleteAll();
  }

  @Test
  @DisplayName("같은 그룹의 custom tag 제목 중복 생성은 거부하고 다른 그룹의 같은 제목은 허용한다")
  void givenExistingTitleInGroup_whenCreateCustomTag_thenRejectsOnlySameGroupDuplicate()
      throws Exception {
    // given
    GroupSpace firstGroup = createGroup("첫 번째 그룹");
    GroupSpace secondGroup = createGroup("두 번째 그룹");
    tagRepository.saveAndFlush(Tag.groupCustom(firstGroup.getId(), "업무", "#111111"));

    // when, then
    mockMvc
        .perform(
            post("/api/group-spaces/{groupSpaceId}/tags", firstGroup.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(customTagRequest("업무", "#222222")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            post("/api/group-spaces/{groupSpaceId}/tags", secondGroup.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(customTagRequest("업무", "#222222")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("업무"));
  }

  @Test
  @DisplayName("그룹 custom tag는 자신의 제목으로 수정할 수 있지만 다른 태그의 제목으로는 수정할 수 없다")
  void givenGroupCustomTags_whenUpdateTitle_thenExcludesOnlyTargetTagFromDuplicateCheck()
      throws Exception {
    // given
    GroupSpace groupSpace = createGroup("그룹");
    Tag targetTag =
        tagRepository.saveAndFlush(Tag.groupCustom(groupSpace.getId(), "업무", "#111111"));
    tagRepository.saveAndFlush(Tag.groupCustom(groupSpace.getId(), "개인", "#222222"));

    // when, then
    mockMvc
        .perform(
            patch(
                    "/api/group-spaces/{groupSpaceId}/tags/{tagId}",
                    groupSpace.getId(),
                    targetTag.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(customTagRequest("업무", "#333333")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(targetTag.getId()))
        .andExpect(jsonPath("$.title").value("업무"));

    mockMvc
        .perform(
            patch(
                    "/api/group-spaces/{groupSpaceId}/tags/{tagId}",
                    groupSpace.getId(),
                    targetTag.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(customTagRequest("개인", "#333333")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
  }

  private GroupSpace createGroup(String name) {
    GroupSpace groupSpace =
        groupSpaceRepository.saveAndFlush(new GroupSpace(currentAccountId(), name, null));
    groupMemberRepository.saveAndFlush(
        new GroupMember(
            groupSpace, currentAccountId(), "멤버", Instant.parse("2026-01-01T00:00:00Z")));
    return groupSpace;
  }

  private String customTagRequest(String title, String colorCode) {
    return """
                {
                  "title": "%s",
                  "colorCode": "%s"
                }
                """
        .formatted(title, colorCode);
  }
}
