package com.calio.calendar.sharing.recurrence.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
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
import java.sql.Types;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(
        name = "personal_recurrence_group_shares",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_personal_recurrence_group_share",
                        columnNames = {"recurrence_event_id", "group_space_id"}
                ),
                @UniqueConstraint(
                        name = "uk_personal_recurrence_group_share_public_id",
                        columnNames = "public_share_id"
                )
        }
)
public class PersonalRecurrenceGroupShare extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurrence_event_id", nullable = false)
    private RecurrenceEvent recurrenceEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_space_id", nullable = false)
    private GroupSpace groupSpace;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "public_share_id", nullable = false, updatable = false, length = 36)
    private UUID publicShareId;

    protected PersonalRecurrenceGroupShare() {
    }

    private PersonalRecurrenceGroupShare(
            RecurrenceEvent recurrenceEvent,
            GroupSpace groupSpace,
            UUID publicShareId
    ) {
        this.recurrenceEvent = recurrenceEvent;
        this.groupSpace = groupSpace;
        this.publicShareId = publicShareId;
    }

    public static PersonalRecurrenceGroupShare create(
            RecurrenceEvent recurrenceEvent,
            GroupSpace groupSpace
    ) {
        return new PersonalRecurrenceGroupShare(recurrenceEvent, groupSpace, UUID.randomUUID());
    }

    public Long getId() {
        return id;
    }

    public RecurrenceEvent getRecurrenceEvent() {
        return recurrenceEvent;
    }

    public GroupSpace getGroupSpace() {
        return groupSpace;
    }

    public UUID getPublicShareId() {
        return publicShareId;
    }
}
