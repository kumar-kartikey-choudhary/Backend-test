package com.pratikdairy.user.repository;

import com.pratikdairy.user.model.PasswordResetToken;
import com.pratikdairy.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** True if this user already asked for a reset after the given moment - used to throttle email. */
    boolean existsByUserAndCreatedAtAfter(User user, ZonedDateTime since);

    /** Makes every still-open link for the user useless (a newer request replaces them, a reset ends them). */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.user = :user AND t.used = false")
    int invalidateOpenFor(@Param("user") User user);
}
