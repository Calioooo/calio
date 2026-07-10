package com.calio.calendar.repository.entity;

import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
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

    protected Tag() {
    }

    public Tag(TagType tagType, String title, String colorCode) {
        this(tagType, title, colorCode, null);
    }

    public Tag(TagType tagType, String title, String colorCode, Account account) {
        this.tagType = tagType;
        this.title = title;
        this.colorCode = new ColorCode(colorCode);
        this.account = account;
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
}
