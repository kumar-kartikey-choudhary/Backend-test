package com.pratikdairy.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pratikdairy.parent.base.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * One "forgot password" request. Only a hash of the emailed token is stored (the same principle as
 * refresh tokens), so reading the database cannot be used to reset someone's password.
 */
@Entity
@Table(name = "PASSWORD_RESET_TOKEN")
@Data
@EqualsAndHashCode(callSuper = false)
public class PasswordResetToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "TOKEN_HASH", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "EXPIRES_AT", nullable = false)
    private LocalDateTime expiresAt;

    /** Set once the link has been used, or replaced by a newer request - a link works exactly once. */
    @Column(name = "USED", columnDefinition = "TINYINT(1) DEFAULT '0'")
    private boolean used = false;
}
