package com.pratikdairy.user.controller;

import com.pratikdairy.user.dto.LoginRequest;
import com.pratikdairy.user.dto.LoginResponse;
import com.pratikdairy.user.dto.RefreshTokenRequest;
import com.pratikdairy.user.dto.RefreshTokenResponse;
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

    @GetMapping(path = "admin/find/{id}")
    ResponseEntity<UserDto> find(@PathVariable("id") String id);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping(path = "admin/findAll")
    ResponseEntity<List<UserDto>> findAll();

    @PatchMapping(path = "update/{id}")
    ResponseEntity<UserDto> update(@Valid @RequestBody UserDto userDto, @PathVariable(name = "id") String id);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping(path = "delete/{id}")
    void delete(@PathVariable(name = "id") String id);
}