package com.calio.calendar.groupcalendar.sharing.recurrence.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "personal_recurrence_group_share_occurrence_overrides",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_personal_recurrence_group_share_occurrence_override",
                columnNames = {"share_id", "origin_start_at"}
        )
)
public class PersonalRecurrenceGroupShareOccurrenceOverride extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "share_id", nullable = false)
    private PersonalRecurrenceGroupShare share;

    @Column(name = "origin_start_at", nullable = false)
    private Instant originStartAt;

    protected PersonalRecurrenceGroupShareOccurrenceOverride() {
    }

    public PersonalRecurrenceGroupShareOccurrenceOverride(
            PersonalRecurrenceGroupShare share,
            Instant originStartAt
    ) {
        this.share = share;
        this.originStartAt = originStartAt;
    }

    public Long getId() {
        return id;
    }

    public PersonalRecurrenceGroupShare getShare() {
        return share;
    }

    public Instant getOriginStartAt() {
        return originStartAt;
    }
}
