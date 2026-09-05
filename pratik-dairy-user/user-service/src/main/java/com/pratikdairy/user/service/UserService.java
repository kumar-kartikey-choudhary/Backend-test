package com.pratikdairy.user.service;

import com.pratikdairy.user.dto.LoginRequest;
import com.pratikdairy.user.dto.LoginResponse;
import com.pratikdairy.user.dto.RefreshTokenRequest;
import com.pratikdairy.user.dto.RefreshTokenResponse;
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
}