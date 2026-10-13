package com.pratikdairy.user.service;

import com.pratikdairy.user.model.User;

/** Email verification and account deletion - the parts of an account's life that aren't plain profile edits. */
public interface AccountService {

    /** Emails a confirmation link to the user's address (skipped if already verified or one was just sent). */
    void sendVerificationEmail(User user);

    /** The signed-in user asks for the confirmation email again. */
    void resendVerification();

    /** Marks the address verified using the token from the emailed link. */
    void verifyEmail(String token);

    /** The signed-in user deletes their own account, after confirming their password. */
    void deleteOwnAccount(String password);

    /**
     * Anonymises an account: personal data, addresses and notifications are erased, every session ends and it
     * can no longer sign in. The row stays (as a tombstone) so the username can't be given to someone else.
     */
    void deactivate(User user);
}
