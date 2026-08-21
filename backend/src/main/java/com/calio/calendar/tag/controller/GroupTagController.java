package com.calio.calendar.tag.controller;
import com.calio.calendar.security.AuthenticatedAccount;
import com.calio.calendar.tag.controller.dto.CustomTagRequest;
import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.tag.service.GroupTagService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/group-spaces/{groupSpaceId}/tags")
public class GroupTagController {
 private final GroupTagService service; public GroupTagController(GroupTagService service){this.service=service;}
 @GetMapping public List<TagResponse> list(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable Long groupSpaceId){return service.list(a.accountId(),groupSpaceId);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public TagResponse create(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable Long groupSpaceId,@Valid @RequestBody CustomTagRequest r){return service.create(a.accountId(),groupSpaceId,r);}
 @PatchMapping("/{tagId}") public TagResponse update(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable Long groupSpaceId,@PathVariable Long tagId,@Valid @RequestBody CustomTagRequest r){return service.update(a.accountId(),groupSpaceId,tagId,r);}
 @DeleteMapping("/{tagId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable Long groupSpaceId,@PathVariable Long tagId){service.delete(a.accountId(),groupSpaceId,tagId);}
}
