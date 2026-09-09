package com.calio.calendar.notification.domain;

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

@Entity
@Table(
        name = "notification_endpoint_deliveries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_endpoint_deliveries",
                columnNames = {"notification_delivery_id", "endpoint_id"}
        )
)
public class NotificationEndpointDelivery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_delivery_id", nullable = false)
    private NotificationDelivery delivery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private IosPushDevice endpoint;

    @Column(nullable = false)
    private String result;

    private String providerRequestId;
    private String failureReason;

    protected NotificationEndpointDelivery() {
    }

    public NotificationEndpointDelivery(
            NotificationDelivery delivery,
            IosPushDevice endpoint,
            String result,
            String providerRequestId,
            String failureReason
    ) {
        this.delivery = delivery;
        this.endpoint = endpoint;
        this.result = result;
        this.providerRequestId = providerRequestId;
        this.failureReason = failureReason;
    }
}
