package com.calio.calendar.tag.domain;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.domain.GroupSpace;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "tags")
public class Tag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TagType tagType;

    @Column(nullable = false)
    private String title;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "color_code", nullable = false, length = 7))
    private ColorCode colorCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_space_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private GroupSpace groupSpace;

    protected Tag() {
    }

    public Tag(TagType tagType, String title, String colorCode) {
        this(tagType, title, colorCode, (Account) null);
    }

    public Tag(TagType tagType, String title, String colorCode, Account account) {
        this(tagType, title, colorCode, account, null);
    }

    public Tag(TagType tagType, String title, String colorCode, GroupSpace groupSpace) {
        this(tagType, title, colorCode, null, groupSpace);
    }

    private Tag(TagType tagType, String title, String colorCode, Account account, GroupSpace groupSpace) {
        validateOwnership(tagType, account, groupSpace);
        this.tagType = tagType;
        this.title = title;
        this.colorCode = new ColorCode(colorCode);
        this.account = account;
        this.groupSpace = groupSpace;
    }

    public void update(String title, String colorCode) {
        if (tagType != TagType.CUSTOM) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }

        this.title = title;
        this.colorCode = new ColorCode(colorCode);
    }

    public Long getId() {
        return id;
    }

    public TagType getTagType() {
        return tagType;
    }

    public String getTitle() {
        return title;
    }

    public String getColorCode() {
        return colorCode.getValue();
    }

    public Account getAccount() {
        return account;
    }

    public GroupSpace getGroupSpace() {
        return groupSpace;
    }

    private void validateOwnership(TagType tagType, Account account, GroupSpace groupSpace) {
        boolean isPersonalDefault = tagType == TagType.PERSONAL_DEFAULT
                && account == null
                && groupSpace == null;
        boolean isPersonalCustom = tagType == TagType.CUSTOM && account != null && groupSpace == null;
        boolean isGroupTag = (tagType == TagType.GROUP_DEFAULT || tagType == TagType.CUSTOM)
                && account == null && groupSpace != null;
        if (!isPersonalDefault && !isPersonalCustom && !isGroupTag) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
