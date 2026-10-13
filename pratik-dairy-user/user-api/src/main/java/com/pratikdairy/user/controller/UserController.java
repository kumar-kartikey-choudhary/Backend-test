package com.pratikdairy.user.controller;

import com.pratikdairy.user.dto.ChangePasswordRequest;
import com.pratikdairy.user.dto.DeleteAccountRequest;
import com.pratikdairy.user.dto.VerifyEmailRequest;
import com.pratikdairy.user.dto.ForgotPasswordRequest;
import com.pratikdairy.user.dto.LoginRequest;
import com.pratikdairy.user.dto.LoginResponse;
import com.pratikdairy.user.dto.RefreshTokenRequest;
import com.pratikdairy.user.dto.RefreshTokenResponse;
import com.pratikdairy.user.dto.ResetPasswordRequest;
import com.pratikdairy.user.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ResponseBody
@FeignClient(name = "PRATIK-DAIRY-USER", contextId = "userClient",  primary = false , url = "${user.url}")
public interface UserController {

    @PostMapping(path = "register")
    ResponseEntity<UserDto> create(@Valid  @RequestBody UserDto userDto);

    @PostMapping(path = "login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest);

    // Exchanges a still-valid refresh token for a new access token, rotating the refresh token
    // in the process (old one revoked, new one issued) - see RefreshToken/JwtUtils for why.
    @PostMapping(path = "refresh-token")
    ResponseEntity<RefreshTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request);

    // Revokes the given refresh token, ending that session. Access tokens already issued stay
    // valid until they naturally expire (they're short-lived and self-contained, not looked up
    // per-request) - this only prevents further refreshes from that token.
    @PostMapping(path = "logout")
    ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request);

    // Lets any authenticated user fetch their own profile without needing ROLE_ADMIN's
    // findAll() - previously the only way for a customer to see their own data.
    @GetMapping(path = "me")
    ResponseEntity<UserDto> me();

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping(path = "admin/find/{id}")
    ResponseEntity<UserDto> find(@PathVariable("id") String id);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping(path = "admin/findAll")
    ResponseEntity<List<UserDto>> findAll();

    // Public. Emails a one-time reset link to the account with this address, if there is one. Always
    // answers 200 whether or not the address is registered, so it can't be used to find out who has an account.
    @PostMapping(path = "forgot-password")
    ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request);

    // Public. Sets a new password using the token from the emailed link, then signs every session out.
    @PostMapping(path = "reset-password")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request);

    // Public. Confirms an email address using the token from the emailed link.
    @PostMapping(path = "verify-email")
    ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request);

    // Signed-in. Sends the caller a fresh verification email (at most one a minute).
    @PostMapping(path = "resend-verification")
    ResponseEntity<Void> resendVerification();

    // Signed-in. Deletes the caller's OWN account after re-checking their password: personal details,
    // addresses and notifications are erased and every session ends. Orders and payments are kept for the
    // shop's records, and the username stays reserved.
    @PostMapping(path = "delete-account")
    ResponseEntity<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest request);

    // Changes the caller's OWN password. Needs the current password, so a stolen session or token alone
    // can't take the account over. Every other session is signed out; the response holds fresh tokens
    // for this one.
    @PostMapping(path = "change-password")
    ResponseEntity<RefreshTokenResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request);

    @PatchMapping(path = "update/{id}")
    ResponseEntity<UserDto> update(@Valid @RequestBody UserDto userDto, @PathVariable(name = "id") String id);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping(path = "delete/{id}")
    void delete(@PathVariable(name = "id") String id);
}