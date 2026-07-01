package com.calio.calendar.repository.entity;

import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.regex.Pattern;

@Entity
@Table(name = "tags")
public class Tag extends BaseEntity {

    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TagType tagType;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 7)
    private String colorCode;

    protected Tag() {
    }

    public Tag(TagType tagType, String title, String colorCode) {
        this.tagType = tagType;
        this.title = title;
        this.colorCode = normalizeColorCode(colorCode);
    }

    @PrePersist
    @PreUpdate
    private void normalizeBeforeSave() {
        colorCode = normalizeColorCode(colorCode);
    }

    private String normalizeColorCode(String colorCode) {
        if (colorCode == null || !COLOR_CODE_PATTERN.matcher(colorCode).matches()) {
            throw new CalioException(ErrorCode.INVALID_TAG_COLOR_CODE);
        }

        return colorCode.toUpperCase(Locale.ROOT);
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
        return colorCode;
    }
}
