package com.calio.calendar.groupspace.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_spaces")
public class GroupSpace extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "owner_account_id", nullable = false)
  private Long ownerAccountId;

  @Embedded
  @AttributeOverride(name = "value", column = @Column(name = "name", nullable = false, length = 30))
  private GroupSpaceName name;

  @Embedded
  @AttributeOverride(name = "value", column = @Column(name = "emoji", length = 64))
  private GroupSpaceEmoji emoji;

  protected GroupSpace() {}

  public GroupSpace(Long ownerAccountId, GroupSpaceName name, GroupSpaceEmoji emoji) {
    this.ownerAccountId = ownerAccountId;
    this.name = name;
    this.emoji = emoji;
  }

  public GroupSpace(Long ownerAccountId, String name, String emoji) {
    this(ownerAccountId, new GroupSpaceName(name), GroupSpaceEmoji.fromNullable(emoji));
  }

  public Long getId() {
    return id;
  }

  public Long getOwnerAccountId() {
    return ownerAccountId;
  }

  public String getName() {
    return name.value();
  }

  public String getEmoji() {
    return emoji == null ? null : emoji.value();
  }

  public void update(GroupSpaceName name, GroupSpaceEmoji emoji) {
    this.name = name;
    this.emoji = emoji;
  }

  public void transferOwnershipTo(Long accountId) {
    this.ownerAccountId = accountId;
  }
}
