package com.calio.calendar.groupspace.controller;

import com.calio.calendar.groupspace.controller.dto.CreateGroupSpaceRequest;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceDetailResponse;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceListResponse;
import com.calio.calendar.groupspace.controller.dto.UpdateGroupSpaceRequest;
import com.calio.calendar.groupspace.usecase.CreateGroupSpaceUseCase;
import com.calio.calendar.groupspace.usecase.DeleteGroupSpaceUseCase;
import com.calio.calendar.groupspace.usecase.GetGroupSpaceUseCase;
import com.calio.calendar.groupspace.usecase.ListMyGroupSpacesUseCase;
import com.calio.calendar.groupspace.usecase.UpdateGroupSpaceUseCase;
import com.calio.calendar.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/group-spaces")
public class GroupSpaceController {

  private final CreateGroupSpaceUseCase createGroupSpaceUseCase;
  private final ListMyGroupSpacesUseCase listMyGroupSpacesUseCase;
  private final GetGroupSpaceUseCase getGroupSpaceUseCase;
  private final UpdateGroupSpaceUseCase updateGroupSpaceUseCase;
  private final DeleteGroupSpaceUseCase deleteGroupSpaceUseCase;

  public GroupSpaceController(
      CreateGroupSpaceUseCase createGroupSpaceUseCase,
      ListMyGroupSpacesUseCase listMyGroupSpacesUseCase,
      GetGroupSpaceUseCase getGroupSpaceUseCase,
      UpdateGroupSpaceUseCase updateGroupSpaceUseCase,
      DeleteGroupSpaceUseCase deleteGroupSpaceUseCase) {
    this.createGroupSpaceUseCase = createGroupSpaceUseCase;
    this.listMyGroupSpacesUseCase = listMyGroupSpacesUseCase;
    this.getGroupSpaceUseCase = getGroupSpaceUseCase;
    this.updateGroupSpaceUseCase = updateGroupSpaceUseCase;
    this.deleteGroupSpaceUseCase = deleteGroupSpaceUseCase;
  }

  @PostMapping
  public ResponseEntity<GroupSpaceDetailResponse> create(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody CreateGroupSpaceRequest request) {
    GroupSpaceDetailResponse response =
        createGroupSpaceUseCase.create(account.accountId(), request);
    URI location = URI.create("/api/group-spaces/" + response.groupSpaceId());
    return ResponseEntity.created(location).body(response);
  }

  @GetMapping
  public GroupSpaceListResponse list(@AuthenticationPrincipal AuthenticatedAccount account) {
    return listMyGroupSpacesUseCase.list(account.accountId());
  }

  @GetMapping("/{groupSpaceId}")
  public GroupSpaceDetailResponse get(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable("groupSpaceId") Long groupSpaceId) {
    return getGroupSpaceUseCase.get(account.accountId(), groupSpaceId);
  }

  @PatchMapping("/{groupSpaceId}")
  public GroupSpaceDetailResponse update(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable("groupSpaceId") Long groupSpaceId,
      @Valid @RequestBody UpdateGroupSpaceRequest request) {
    return updateGroupSpaceUseCase.update(account.accountId(), groupSpaceId, request);
  }

  @DeleteMapping("/{groupSpaceId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable("groupSpaceId") Long groupSpaceId) {
    deleteGroupSpaceUseCase.delete(account.accountId(), groupSpaceId);
    return ResponseEntity.noContent().build();
  }
}
