package com.calio.calendar.tag.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;

@Entity
@Table(name = "tags")
public class Tag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TagType tagType;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "title", nullable = false, length = 255))
    private TagTitle title;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "color_code", nullable = false, length = 7))
    private ColorCode colorCode;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "group_space_id")
    private Long groupSpaceId;

    @Column(name = "is_fallback", nullable = false)
    private boolean fallback;

    protected Tag() {
    }

    public static Tag personalDefault(String title, String colorCode) {
        return new Tag(TagType.PERSONAL_DEFAULT, title, colorCode, null, null, false);
    }

    public static Tag personalFallback(String title, String colorCode) {
        return new Tag(TagType.PERSONAL_DEFAULT, title, colorCode, null, null, true);
    }

    public static Tag personalCustom(Long accountId, String title, String colorCode) {
        return new Tag(TagType.CUSTOM, title, colorCode, accountId, null, false);
    }

    public static Tag groupDefault(Long groupSpaceId) {
        return new Tag(TagType.GROUP_DEFAULT, "기타", "#64748B", null, groupSpaceId, true);
    }

    public static Tag groupCustom(Long groupSpaceId, String title, String colorCode) {
        return new Tag(TagType.CUSTOM, title, colorCode, null, groupSpaceId, false);
    }

    private Tag(
            TagType tagType,
            String title,
            String colorCode,
            Long accountId,
            Long groupSpaceId,
            boolean fallback
    ) {
        validateOwnership(tagType, accountId, groupSpaceId);
        validateFallbackRole(tagType, accountId, groupSpaceId, fallback);
        this.tagType = tagType;
        this.title = new TagTitle(title);
        this.colorCode = new ColorCode(colorCode);
        this.accountId = accountId;
        this.groupSpaceId = groupSpaceId;
        this.fallback = fallback;
    }

    public void update(String title, String colorCode) {
        if (tagType != TagType.CUSTOM) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }

        this.title = new TagTitle(title);
        this.colorCode = new ColorCode(colorCode);
    }

    public Long getId() {
        return id;
    }

    public TagType getTagType() {
        return tagType;
    }

    public String getTitle() {
        return title.value();
    }

    public String getColorCode() {
        return colorCode.getValue();
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getGroupSpaceId() {
        return groupSpaceId;
    }

    public boolean isFallback() {
        return fallback;
    }

    @PostLoad
    private void validatePersistentState() {
        validateOwnership(tagType, accountId, groupSpaceId);
        validateFallbackRole(tagType, accountId, groupSpaceId, fallback);
    }

    private void validateOwnership(TagType tagType, Long accountId, Long groupSpaceId) {
        boolean isPersonalDefault = tagType == TagType.PERSONAL_DEFAULT
                && accountId == null
                && groupSpaceId == null;
        boolean isPersonalCustom = tagType == TagType.CUSTOM && accountId != null && groupSpaceId == null;
        boolean isGroupTag = (tagType == TagType.GROUP_DEFAULT || tagType == TagType.CUSTOM)
                && accountId == null && groupSpaceId != null;
        if (!isPersonalDefault && !isPersonalCustom && !isGroupTag) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private void validateFallbackRole(
            TagType tagType,
            Long accountId,
            Long groupSpaceId,
            boolean fallback
    ) {
        boolean isPersonalFallback = fallback
                && tagType == TagType.PERSONAL_DEFAULT
                && accountId == null
                && groupSpaceId == null;
        boolean isGroupFallback = fallback
                && tagType == TagType.GROUP_DEFAULT
                && accountId == null
                && groupSpaceId != null;
        boolean doesNotRequireFallback = !fallback && tagType != TagType.GROUP_DEFAULT;
        if (!isPersonalFallback && !isGroupFallback && !doesNotRequireFallback) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
