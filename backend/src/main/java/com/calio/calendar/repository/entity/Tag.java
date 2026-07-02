package com.calio.calendar.repository.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    protected Tag() {
    }

    public Tag(TagType tagType, String title, String colorCode) {
        this.tagType = tagType;
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
}
