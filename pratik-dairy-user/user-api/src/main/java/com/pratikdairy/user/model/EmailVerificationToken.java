package com.pratikdairy.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pratikdairy.parent.base.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** One "please confirm this email address" link. Only a hash of the emailed token is stored. */
@Entity
@Table(name = "EMAIL_VERIFICATION_TOKEN")
@Data
@EqualsAndHashCode(callSuper = false)
public class EmailVerificationToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "TOKEN_HASH", nullable = false, unique = true)
    private String tokenHash;

    // The address this link confirms. If the customer has changed their email since, the link is dead - it
    // must not mark a different, never-confirmed address as verified.
    @Column(name = "EMAIL", nullable = false)
    private String email;

    @Column(name = "EXPIRES_AT", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "USED", columnDefinition = "TINYINT(1) DEFAULT '0'")
    private boolean used = false;
}
