package com.pratikdairy.user.service.impl;

import com.pratikdairy.parent.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after {@value #MAX_FAILURES} wrong passwords for a username within
 * {@value #WINDOW_MINUTES} minutes, that username is locked for {@value #LOCK_MINUTES} minutes and sign-in
 * answers 429 - even for the right password, so a guesser learns nothing from the lock.
 *
 * Keyed by the username typed, whether or not such an account exists, so the lock can't be used to find
 * out which usernames are registered. (The trade-off of any per-account lock: someone can lock a known
 * username out for a while by failing on purpose. A lock is lifted by waiting it out or a password reset.)
 *
 * Kept in memory: it resets when the service restarts and is not shared between instances. That is
 * enough to stop online guessing against one instance; share it (e.g. in Redis) if you run several.
 */
@Component
public class LoginAttemptTracker {

    static final int MAX_FAILURES = 5;
    static final long WINDOW_MINUTES = 15;
    static final long LOCK_MINUTES = 15;
    private static final int PURGE_ABOVE = 10_000;

    private record Attempts(int failures, Instant windowStart, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, Attempts> byUsername = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public LoginAttemptTracker() {
        this(Clock.systemUTC());
    }

    LoginAttemptTracker(Clock clock) {
        this.clock = clock;
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    /** @throws TooManyRequestsException if this username is currently locked */
    public void checkAllowed(String username) {
        Attempts attempts = byUsername.get(key(username));
        Instant now = clock.instant();
        if (attempts != null && attempts.lockedUntil() != null && attempts.lockedUntil().isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, attempts.lockedUntil()).plusSeconds(59).toMinutes());
            throw new TooManyRequestsException("Too many failed sign-in attempts. Please try again in "
                    + minutes + (minutes == 1 ? " minute" : " minutes") + ", or reset your password.");
        }
    }

    public void recordFailure(String username) {
        Instant now = clock.instant();
        if (byUsername.size() > PURGE_ABOVE) {
            byUsername.values().removeIf(a -> expired(a, now));
        }
        byUsername.compute(key(username), (k, current) -> {
            if (current == null || expired(current, now)) {
                return new Attempts(1, now, null);
            }
            int failures = current.failures() + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? now.plus(Duration.ofMinutes(LOCK_MINUTES)) : current.lockedUntil();
            return new Attempts(failures, current.windowStart(), lockedUntil);
        });
    }

    public void recordSuccess(String username) {
        byUsername.remove(key(username));
    }

    /** A password reset proves the owner is back, so any lock is lifted. */
    public void clear(String username) {
        recordSuccess(username);
    }

    private boolean expired(Attempts a, Instant now) {
        if (a.lockedUntil() != null) {
            return !a.lockedUntil().isAfter(now);
        }
        return a.windowStart().plus(Duration.ofMinutes(WINDOW_MINUTES)).isBefore(now);
    }
}
