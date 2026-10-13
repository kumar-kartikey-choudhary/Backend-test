package com.pratikdairy.user.service.impl;

import com.pratikdairy.parent.exception.BadRequestException;
import com.pratikdairy.parent.exception.InvalidStateException;
import com.pratikdairy.parent.exception.ResourceNotFoundException;
import com.pratikdairy.parent.exception.TooManyRequestsException;
import com.pratikdairy.parent.exception.UnauthorizedException;
import com.pratikdairy.user.jwt.JwtUtils;
import com.pratikdairy.user.mail.EmailSender;
import com.pratikdairy.user.model.EmailVerificationToken;
import com.pratikdairy.user.model.User;
import com.pratikdairy.user.repository.AddressRepository;
import com.pratikdairy.user.repository.EmailVerificationTokenRepository;
import com.pratikdairy.user.repository.NotificationRepository;
import com.pratikdairy.user.repository.PasswordResetTokenRepository;
import com.pratikdairy.user.repository.RefreshTokenRepository;
import com.pratikdairy.user.repository.UserRepository;
import com.pratikdairy.user.service.AccountService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

@Service
@Slf4j
public class AccountServiceImpl implements AccountService {

    private static final long RESEND_THROTTLE_SECONDS = 60;

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final RefreshTokenRepository refreshTokens;
    private final AddressRepository addresses;
    private final NotificationRepository notifications;
    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;
    private final EmailSender emailSender;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl = "http://localhost:4200";

    @Value("${app.email-verification.expiry-hours:24}")
    private long verifyExpiryHours = 24;

    @Autowired
    public AccountServiceImpl(UserRepository userRepository, EmailVerificationTokenRepository verificationTokens,
                              PasswordResetTokenRepository resetTokens, RefreshTokenRepository refreshTokens,
                              AddressRepository addresses, NotificationRepository notifications,
                              PasswordEncoder encoder, JwtUtils jwtUtils, EmailSender emailSender) {
        this.userRepository = userRepository;
        this.verificationTokens = verificationTokens;
        this.resetTokens = resetTokens;
        this.refreshTokens = refreshTokens;
        this.addresses = addresses;
        this.notifications = notifications;
        this.encoder = encoder;
        this.jwtUtils = jwtUtils;
        this.emailSender = emailSender;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new UnauthorizedException("User not authenticated");
        }
        return userRepository.findByUsername(auth.getName())
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + auth.getName()));
    }

    // ------------------------------------------------------------------ verification

    @Override
    @Transactional
    public void sendVerificationEmail(User user) {
        if (user.isEmailVerified()) {
            return;
        }
        if (verificationTokens.existsByUserAndCreatedAtAfter(user, ZonedDateTime.now().minusSeconds(RESEND_THROTTLE_SECONDS))) {
            log.info("Verification email for '{}' skipped: one was sent in the last minute", user.getUsername());
            return;
        }
        issueAndSend(user);
    }

    private void issueAndSend(User user) {
        verificationTokens.invalidateOpenFor(user); // a newer link replaces any older one
        String rawToken = jwtUtils.generateRefreshToken(); // 48 random bytes, URL-safe
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(jwtUtils.hashToken(rawToken));
        token.setEmail(user.getEmail());
        token.setExpiresAt(LocalDateTime.now().plusHours(verifyExpiryHours));
        verificationTokens.saveAndFlush(token);

        String link = frontendUrl.replaceAll("/+$", "") + "/verify-email?token=" + rawToken;
        emailSender.sendEmailVerification(user.getEmail(), user.getFirstName(), link, Duration.ofHours(verifyExpiryHours));
        log.info("Verification link issued for '{}'", user.getUsername());
    }

    @Override
    @Transactional
    public void resendVerification() {
        User user = currentUser();
        if (user.isEmailVerified()) {
            throw new InvalidStateException("Your email address is already verified.");
        }
        if (verificationTokens.existsByUserAndCreatedAtAfter(user, ZonedDateTime.now().minusSeconds(RESEND_THROTTLE_SECONDS))) {
            throw new TooManyRequestsException("A verification email was just sent. Please wait a minute before asking for another.");
        }
        issueAndSend(user);
    }

    @Override
    @Transactional
    public void verifyEmail(String rawToken) {
        // One message for unknown, used, expired and out-of-date links.
        BadRequestException invalid = new BadRequestException("This verification link is invalid or has expired. Please request a new one.");
        EmailVerificationToken token = verificationTokens.findByTokenHash(jwtUtils.hashToken(rawToken)).orElseThrow(() -> invalid);
        User user = token.getUser();
        if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now())
                || user.isDeleted() || !token.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw invalid;
        }
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);
        verificationTokens.invalidateOpenFor(user);
        log.info("Email verified for '{}'", user.getUsername());
    }

    // -------------------------------------------------------------------- deletion

    @Override
    @Transactional
    public void deleteOwnAccount(String password) {
        User user = currentUser();
        if (!encoder.matches(password, user.getPassword())) {
            // 400, not 401: a 401 would make the browser think the session expired.
            throw new BadRequestException("Your password is incorrect.");
        }
        deactivate(user);
    }

    @Override
    @Transactional
    public void deactivate(User user) {
        if (user.isDeleted()) {
            return;
        }
        if ("ADMIN".equalsIgnoreCase(user.getRole()) && userRepository.countByRoleIgnoreCaseAndDeletedFalse("ADMIN") <= 1) {
            throw new InvalidStateException("This is the only admin account, so it can't be deleted.");
        }
        String username = user.getUsername();
        log.info("Deleting account '{}'", username);

        addresses.deleteAll(addresses.findByUser_Username(username));
        notifications.deleteByUser_Username(username);
        refreshTokens.revokeAllForUser(user);          // every session ends
        resetTokens.invalidateOpenFor(user);
        verificationTokens.invalidateOpenFor(user);

        // Orders and payments refer to the username, so the row stays - but with nothing personal left in it,
        // and no way to sign in. The username remains reserved, so no new customer can inherit those orders.
        user.setDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        user.setFirstName("Deleted");
        user.setMiddleName(null);
        user.setLastName("User");
        user.setPhoneNumber(null);
        user.setEmail("deleted-" + user.getId() + "@deleted.invalid");
        user.setEmailVerified(false);
        user.setPassword(encoder.encode(UUID.randomUUID().toString()));
        userRepository.saveAndFlush(user);
    }
}
