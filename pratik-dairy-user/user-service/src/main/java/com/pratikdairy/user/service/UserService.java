package com.pratikdairy.user.service;

import com.pratikdairy.user.dto.ChangePasswordRequest;
import com.pratikdairy.user.dto.ForgotPasswordRequest;
import com.pratikdairy.user.dto.LoginRequest;
import com.pratikdairy.user.dto.LoginResponse;
import com.pratikdairy.user.dto.RefreshTokenRequest;
import com.pratikdairy.user.dto.RefreshTokenResponse;
import com.pratikdairy.user.dto.ResetPasswordRequest;
import com.pratikdairy.user.dto.UserDto;

import java.util.List;

public interface UserService {

    UserDto create(UserDto userDto);

    UserDto find(String id);

    UserDto findByUsername(String username);

    List<UserDto> findAll();

    UserDto update(UserDto userDto, String id);

    void delete(String id);

    LoginResponse login(LoginRequest loginRequest);

    RefreshTokenResponse refreshAccessToken(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);

    /**
     * Starts a "forgot password" reset: emails a one-time link if an account has this address. Returns
     * normally either way, so callers can't tell whether the address is registered.
     */
    void requestPasswordReset(ForgotPasswordRequest request);

    /** Sets a new password from an emailed token (single use, time-limited) and signs every session out. */
    void resetPassword(ResetPasswordRequest request);

    /**
     * Changes the signed-in user's own password after checking the current one, signs every other
     * session out, and returns fresh tokens so the session making the change carries on.
     */
    RefreshTokenResponse changePassword(ChangePasswordRequest request);
}