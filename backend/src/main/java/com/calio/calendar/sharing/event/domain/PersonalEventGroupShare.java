package com.calio.calendar.sharing.event.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.groupspace.domain.GroupSpace;
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
        name = "personal_event_group_shares",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_personal_event_group_share",
                        columnNames = {"event_id", "group_space_id"}
                ),
                @UniqueConstraint(
                        name = "uk_personal_event_group_share_public_id",
                        columnNames = "public_share_id"
                )
        }
)
public class PersonalEventGroupShare extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_space_id", nullable = false)
    private GroupSpace groupSpace;

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "public_share_id", nullable = false, updatable = false, length = 36)
    private UUID publicShareId;

    protected PersonalEventGroupShare() {
    }

    private PersonalEventGroupShare(Event event, GroupSpace groupSpace, UUID publicShareId) {
        this.event = event;
        this.groupSpace = groupSpace;
        this.publicShareId = publicShareId;
    }

    public static PersonalEventGroupShare create(Event event, GroupSpace groupSpace) {
        return new PersonalEventGroupShare(event, groupSpace, UUID.randomUUID());
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public GroupSpace getGroupSpace() {
        return groupSpace;
    }

    public UUID getPublicShareId() {
        return publicShareId;
    }
}
