package com.pratikdairy.user.model;

import com.pratikdairy.parent.base.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** A message sent through the website's contact form, kept so the shop can read and answer it. */
@Entity
@Table(name = "CONTACT_MESSAGE")
@Data
@EqualsAndHashCode(callSuper = false)
public class ContactMessage extends BaseEntity {

    @Column(name = "NAME", columnDefinition = "VARCHAR(100) NOT NULL", nullable = false)
    private String name;

    @Column(name = "EMAIL", columnDefinition = "VARCHAR(100) NOT NULL", nullable = false)
    private String email;

    @Column(name = "PHONE", columnDefinition = "VARCHAR(20)")
    private String phone;

    @Column(name = "MESSAGE", columnDefinition = "VARCHAR(2000) NOT NULL", nullable = false)
    private String message;

    // The shop has dealt with it (answered, or nothing to do).
    @Column(name = "HANDLED", columnDefinition = "TINYINT(1) DEFAULT '0'")
    private boolean handled = false;
}
