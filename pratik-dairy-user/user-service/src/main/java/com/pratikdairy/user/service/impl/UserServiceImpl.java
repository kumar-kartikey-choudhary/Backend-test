package com.pratikdairy.user.service.impl;

import com.pratikdairy.parent.exception.BadRequestException;
import com.pratikdairy.parent.exception.DuplicateResourceException;
import com.pratikdairy.parent.exception.OperationFailedException;
import com.pratikdairy.parent.exception.ResourceNotFoundException;
import com.pratikdairy.parent.exception.UnauthorizedException;
import com.pratikdairy.parent.utility.MapperUtility;
import com.pratikdairy.user.dto.ChangePasswordRequest;
import com.pratikdairy.user.dto.ForgotPasswordRequest;
import com.pratikdairy.user.dto.LoginRequest;
import com.pratikdairy.user.dto.LoginResponse;
import com.pratikdairy.user.dto.RefreshTokenRequest;
import com.pratikdairy.user.dto.RefreshTokenResponse;
import com.pratikdairy.user.dto.ResetPasswordRequest;
import com.pratikdairy.user.dto.UserDto;
import com.pratikdairy.user.jwt.JwtUtils;
import com.pratikdairy.user.mail.EmailSender;
import com.pratikdairy.user.model.PasswordResetToken;
import com.pratikdairy.user.model.RefreshToken;
import com.pratikdairy.user.model.User;
import com.pratikdairy.user.repository.PasswordResetTokenRepository;
import com.pratikdairy.user.repository.RefreshTokenRepository;
import com.pratikdairy.user.repository.UserRepository;
import com.pratikdairy.user.service.AccountService;
import com.pratikdairy.user.service.UserService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder encoder;
    private final AuthenticationManager manager;
    private final JwtUtils jwtUtils;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    // Where the emailed link points (the Angular app), and how long it stays valid.
    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl = "http://localhost:4200";

    @Value("${app.password-reset.expiry-minutes:30}")
    private long resetExpiryMinutes = 30;

    // One reset email per account per minute: stops "forgot password" being used to flood an inbox.
    private static final long RESET_THROTTLE_SECONDS = 60;

    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailSender emailSender;
    private final LoginAttemptTracker loginAttempts;
    private final AccountService accountService;



    @Autowired
    public UserServiceImpl(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder encoder, AuthenticationManager manager, JwtUtils jwtUtils,
                           PasswordResetTokenRepository resetTokenRepository, EmailSender emailSender,
                           LoginAttemptTracker loginAttempts, AccountService accountService)
    {
        this.accountService = accountService;
        this.loginAttempts = loginAttempts;
        this.resetTokenRepository = resetTokenRepository;
        this.emailSender = emailSender;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.encoder = encoder;
        this.manager = manager;
        this.jwtUtils = jwtUtils;
    }


    @Override
    @Transactional
    public UserDto create(UserDto userDto) {
        log.info("Inside @class UserServiceImpl @method create @Param username :{}", userDto.getUsername());
        validateRegistration(userDto);
        if (userRepository.findByUsername(userDto.getUsername()).isPresent()) {
            throw new DuplicateResourceException("Username '" + userDto.getUsername() + "' already exists. Please choose a different username.");
        }
        if (userRepository.existsByEmail(userDto.getEmail())) {
            throw new DuplicateResourceException("An account with email '" + userDto.getEmail() + "' already exists.");
        }
        User user = toNewUser(userDto);
        user.setRole("CUSTOMER");
        user.setPassword(encoder.encode(userDto.getPassword()));
        log.info("Save user object to db");
        User saved = userRepository.saveAndFlush(user);
        sendVerificationQuietly(saved);
        return toDto(saved);
    }

    // Registration must not fail because a confirmation email couldn't be queued.
    private void sendVerificationQuietly(User user) {
        try {
            accountService.sendVerificationEmail(user);
        } catch (RuntimeException e) {
            log.warn("Could not issue a verification email for '{}': {}", user.getUsername(), e.getMessage());
        }
    }



    @Override
    public UserDto find(String id) {
        log.info("Inside @class UserServiceImpl @method find @Param id :{}", id);
        return toDto(getUserOrThrow(id));
    }

    @Override
    public List<UserDto> findAll() {
        log.info("Inside @class UserServiceImpl @method findAll");
        return this.userRepository.findByDeletedFalse().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public UserDto update(UserDto userDto, String id) {
        log.info("Inside @class UserServiceImpl @method update @Param id :{}", id);
        User user = getUserOrThrow(id);
        boolean emailChanged = false;

        // PATCH: a field that isn't in the request is left alone (null means "not sent"). The ones every
        // account must have can't be blanked; the optional ones (middle name, phone) are cleared by sending "".
        if (userDto.getFirstName() != null) {
            requireText(userDto.getFirstName(), "First name");
            user.setFirstName(userDto.getFirstName().trim());
        }
        if (userDto.getLastName() != null) {
            requireText(userDto.getLastName(), "Last name");
            user.setLastName(userDto.getLastName().trim());
        }
        if (userDto.getMiddleName() != null) {
            user.setMiddleName(userDto.getMiddleName().isBlank() ? null : userDto.getMiddleName().trim());
        }
        if (userDto.getPhoneNumber() != null) {
            user.setPhoneNumber(userDto.getPhoneNumber().isBlank() ? null : userDto.getPhoneNumber().trim());
        }
        // username is deliberately not updatable: it is the JWT subject and the ownership
        // key, and update() doesn't check uniqueness the way create() does.
        if (userDto.getEmail() != null) {
            requireText(userDto.getEmail(), "Email");
            if (!userDto.getEmail().contains("@")) {
                throw new BadRequestException("Please enter a valid email address.");
            }
            if (!userDto.getEmail().equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(userDto.getEmail())) {
                throw new DuplicateResourceException("An account with email '" + userDto.getEmail() + "' already exists.");
            }
            if (!userDto.getEmail().trim().equalsIgnoreCase(user.getEmail())) {
                emailChanged = true;
                user.setEmailVerified(false);   // the new address has not been confirmed
            }
            user.setEmail(userDto.getEmail().trim());
        }
        if (userDto.getPassword() != null && !userDto.getPassword().isBlank()) {
            if (userDto.getPassword().length() < MIN_PASSWORD_LENGTH) {
                throw new BadRequestException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters long.");
            }
            user.setPassword(encoder.encode(userDto.getPassword()));
        }
        User saved = this.userRepository.saveAndFlush(user);
        if (emailChanged) {
            sendVerificationQuietly(saved);
        }
        log.info("User with id {} updated successfully", id);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void delete(String id) {
        log.info("Inside @class UserServiceImpl @method delete @Param id :{}", id);
        User user = getUserOrThrow(id);   // a missing id is a clean 404
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && user.getUsername().equals(auth.getName())) {
            throw new BadRequestException("You can't delete your own account from here. Use \"Delete my account\" on your profile.");
        }
        accountService.deactivate(user);
        log.info("User with id {} deleted successfully", id);
    }

    @Override
    public UserDto findByUsername(String username) {
        log.info("Inside @class UserServiceImpl @method findByUsername @Param username :{}", username);
        User user = this.userRepository.findByUsername(username)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return toDto(user);
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest loginRequest) {
        // Only the username is logged - LoginRequest.toString() would include the password.
        log.info("Inside @class UserServiceImpl @method login @Param username :{}", loginRequest.getUsername());
        loginAttempts.checkAllowed(loginRequest.getUsername());
        Authentication authentication;
        try {
            authentication = manager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );
        } catch (AuthenticationException e) {
            // Same message for "no such user" and "wrong password" so it can't be used to probe usernames.
            log.warn("Failed login attempt for username '{}'", loginRequest.getUsername());
            loginAttempts.recordFailure(loginRequest.getUsername());
            throw new UnauthorizedException("Invalid username or password");
        }
        loginAttempts.recordSuccess(loginRequest.getUsername());
        User userDetails = (User) authentication.getPrincipal();
        String username = userDetails.getUsername();
        String userRole = userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList().getFirst();
        log.info("role :{}", userRole);
        String jwtToken = jwtUtils.generateToken(username, userRole);
        String rawRefreshToken = issueRefreshToken(userDetails);
        log.info("User '{}' logged in successfully with role: {}", username, userRole);
        return LoginResponse.builder()
                .username(userDetails.getUsername())
                .role(userRole)
                .token(jwtToken)
                .refreshToken(rawRefreshToken)
                .build();
    }

    @Override
    @Transactional
    public RefreshTokenResponse refreshAccessToken(RefreshTokenRequest request) {
        log.info("Inside @class UserServiceImpl @method refreshAccessToken");
        RefreshToken existing = refreshTokenRepository.findByTokenHash(jwtUtils.hashToken(request.getRefreshToken()))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (existing.isRevoked()) {
            throw new UnauthorizedException("Refresh token has been revoked");
        }
        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token has expired");
        }

        User user = existing.getUser();
        // Same "ROLE_" + role.toUpperCase() shape as User.getAuthorities() / login() above -
        // has to match exactly, since every downstream service's JWT filter reads this claim
        // the same way regardless of whether the access token came from login() or here.
        String userRole = "ROLE_" + user.getRole().toUpperCase();
        String newAccessToken = jwtUtils.generateToken(user.getUsername(), userRole);

        // Rotate: revoke the token that was just used, issue a fresh one. Limits how long a
        // stolen refresh token stays useful - each one is single-use.
        existing.setRevoked(true);
        refreshTokenRepository.saveAndFlush(existing);
        String newRawRefreshToken = issueRefreshToken(user);

        return RefreshTokenResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRawRefreshToken)
                .username(user.getUsername())
                .role(userRole)
                .build();
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        log.info("Inside @class UserServiceImpl @method logout");
        refreshTokenRepository.findByTokenHash(jwtUtils.hashToken(request.getRefreshToken()))
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.saveAndFlush(rt);
                });
        // Silently no-ops if the token doesn't exist/was already revoked - logout should be
        // idempotent and shouldn't leak whether a given token string was ever valid.
    }

    @Override
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        String email = request.getEmail().trim();
        // Nothing below may reveal whether the address is registered: same outcome, same response.
        User user = userRepository.findByEmailIgnoreCase(email).filter(u -> !u.isDeleted()).orElse(null);
        if (user == null) {
            log.info("Password reset requested for an address with no account");
            return;
        }
        if (resetTokenRepository.existsByUserAndCreatedAtAfter(user, ZonedDateTime.now().minusSeconds(RESET_THROTTLE_SECONDS))) {
            log.info("Password reset for '{}' skipped: one was requested in the last minute", user.getUsername());
            return;
        }

        resetTokenRepository.invalidateOpenFor(user); // a newer link replaces any older one
        String rawToken = jwtUtils.generateRefreshToken(); // 48 random bytes, URL-safe
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(jwtUtils.hashToken(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(resetExpiryMinutes));
        resetTokenRepository.saveAndFlush(token);

        String link = frontendUrl.replaceAll("/+$", "") + "/reset-password?token=" + rawToken;
        emailSender.sendPasswordReset(user.getEmail(), user.getFirstName(), link, Duration.ofMinutes(resetExpiryMinutes));
        log.info("Password reset link issued for '{}'", user.getUsername());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // One message for unknown, used and expired links, so it can't be used to probe tokens.
        BadRequestException invalid = new BadRequestException(
                "This reset link is invalid or has expired. Please request a new one.");
        PasswordResetToken token = resetTokenRepository.findByTokenHash(jwtUtils.hashToken(request.getToken()))
                .orElseThrow(() -> invalid);
        if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw invalid;
        }
        String newPassword = request.getNewPassword();
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters long.");
        }

        User user = token.getUser();
        user.setPassword(encoder.encode(newPassword));
        userRepository.saveAndFlush(user);
        emailSender.sendPasswordChanged(user.getEmail(), user.getFirstName());
        loginAttempts.clear(user.getUsername());               // the owner is back: lift any sign-in lock
        resetTokenRepository.invalidateOpenFor(user);          // this link, and any other open one
        int signedOut = refreshTokenRepository.revokeAllForUser(user); // whoever had the old password is out
        log.info("Password reset for '{}'; {} session(s) signed out", user.getUsername(), signedOut);
    }

    @Override
    @Transactional
    public RefreshTokenResponse changePassword(ChangePasswordRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new UnauthorizedException("User not authenticated");
        }
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + auth.getName()));
        log.info("Inside @class UserServiceImpl @method changePassword @Param username :{}", user.getUsername());

        // A wrong current password is a 400, not a 401: a 401 would make the browser think the session
        // expired and try to refresh or sign the user out.
        if (!encoder.matches(request.getCurrentPassword(), user.getPassword())) {
            log.warn("Password change refused for '{}': current password did not match", user.getUsername());
            throw new BadRequestException("Your current password is incorrect.");
        }
        String newPassword = request.getNewPassword();
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters long.");
        }
        if (newPassword.equals(request.getCurrentPassword())) {
            throw new BadRequestException("Your new password must be different from your current one.");
        }

        user.setPassword(encoder.encode(newPassword));
        userRepository.saveAndFlush(user);
        emailSender.sendPasswordChanged(user.getEmail(), user.getFirstName());   // so the owner notices if it wasn't them
        // Anyone holding an old refresh token (another device, a thief) is signed out; this session
        // gets a new one so it isn't interrupted.
        int signedOut = refreshTokenRepository.revokeAllForUser(user);
        log.info("Password changed for '{}'; {} session(s) signed out", user.getUsername(), signedOut);

        String role = "ROLE_" + user.getRole().toUpperCase();
        return RefreshTokenResponse.builder()
                .token(jwtUtils.generateToken(user.getUsername(), role))
                .refreshToken(issueRefreshToken(user))
                .username(user.getUsername())
                .role(role)
                .build();
    }

    private User getUserOrThrow(String id) {
        if (id == null) {
            throw new BadRequestException("User id can not be null");
        }
        return this.userRepository.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    // MapperUtility's reflection-based copy declares checked exceptions; none can occur for
    // User/UserDto (public no-arg constructors), so a failure here is a programming error.
    private UserDto toDto(User user) {
        try {
            return MapperUtility.sourceToTarget(user, UserDto.class, "password");
        } catch (ReflectiveOperationException e) {
            throw new OperationFailedException("Failed to map user", e);
        }
    }

    private static final int MIN_PASSWORD_LENGTH = 8;

    // UserDto carries no bean-validation annotations (it is shared with the partial-update PATCH),
    // so registration checks what it needs itself. Without this an empty username or password
    // would be accepted.
    private void validateRegistration(UserDto userDto) {
        requireText(userDto.getUsername(), "Username");
        requireText(userDto.getFirstName(), "First name");
        requireText(userDto.getLastName(), "Last name");
        requireText(userDto.getEmail(), "Email");
        if (!userDto.getEmail().contains("@")) {
            throw new BadRequestException("Please enter a valid email address.");
        }
        if (userDto.getPassword() == null || userDto.getPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters long.");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(label + " is required.");
        }
    }

    // Registration builds a brand-new user from the request. The identity, audit and role fields are
    // never taken from the client: copying `id` would make the save below merge over an existing
    // account (whoever knows a user's id could overwrite its username, email and password).
    private User toNewUser(UserDto userDto) {
        try {
            return MapperUtility.sourceToTarget(userDto, User.class,
                    "id", "createdBy", "createdAt", "modifiedBy", "modifiedAt", "role",
                    "emailVerified", "deleted", "deletedAt");   // a client cannot register itself as verified
        } catch (ReflectiveOperationException e) {
            throw new OperationFailedException("Failed to map user", e);
        }
    }

    private String issueRefreshToken(User user) {
        String raw = jwtUtils.generateRefreshToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(jwtUtils.hashToken(raw));
        refreshToken.setExpiresAt(LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000));
        refreshTokenRepository.saveAndFlush(refreshToken);
        return raw;
    }
}