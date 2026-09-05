package com.pratikdairy.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pratikdairy.parent.base.entity.BaseEntity;
import com.pratikdairy.user.enums.NotificationType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

// Was entirely missing before - order status changes, payment results, delivery reminders all
// happened with no in-app record a customer could look back at. referenceId is a loose pointer
// (e.g. an order id) back to whatever this notification is about - a plain string, not a FK,
// since the referenced entity usually lives in a different microservice's database (same
// cross-service pattern used throughout this app).
@Entity
@Table(name = "NOTIFICATION")
@Data
@EqualsAndHashCode(callSuper = false)
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    @JsonIgnore
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", columnDefinition = "VARCHAR(20) NOT NULL", nullable = false)
    private NotificationType type;

    @Column(name = "TITLE", columnDefinition = "VARCHAR(150) NOT NULL", nullable = false)
    private String title;

    @Column(name = "MESSAGE", columnDefinition = "VARCHAR(1000)")
    private String message;

    @Column(name = "IS_READ", columnDefinition = "TINYINT(1) DEFAULT '0'")
    private boolean read = false;

    @Column(name = "REFERENCE_ID")
    private String referenceId;
}