package com.pratikdairy.user.service.impl;

import com.pratikdairy.parent.utility.MapperUtility;
import com.pratikdairy.user.dto.LoginRequest;
import com.pratikdairy.user.dto.LoginResponse;
import com.pratikdairy.user.dto.RefreshTokenRequest;
import com.pratikdairy.user.dto.RefreshTokenResponse;
import com.pratikdairy.user.dto.UserDto;
import com.pratikdairy.user.jwt.JwtUtils;
import com.pratikdairy.user.model.RefreshToken;
import com.pratikdairy.user.model.User;
import com.pratikdairy.user.repository.RefreshTokenRepository;
import com.pratikdairy.user.repository.UserRepository;
import com.pratikdairy.user.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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



    @Autowired
    public UserServiceImpl(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder encoder, AuthenticationManager manager, JwtUtils jwtUtils)
    {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.encoder = encoder;
        this.manager = manager;
        this.jwtUtils = jwtUtils;
    }


    @Override
    @Transactional
    public UserDto create(UserDto userDto) {
        log.info("Inside @class UserServiceImpl @method create @Param userDto :{}", userDto);
//       Duplicate username check
        if (userRepository.findByUsername(userDto.getUsername()).isPresent()) {
            throw new RuntimeException("Username '" + userDto.getUsername() + "' already exists. Please choose a different username.");
        }
        try {
            User user = MapperUtility.sourceToTarget(userDto, User.class);
            user.setRole("CUSTOMER");
            user.setPassword(encoder.encode(userDto.getPassword()));
            log.info("Save user object to db");
            try{
                user = userRepository.saveAndFlush(user);
            } catch (Exception e) {
                throw new RuntimeException("Not save to db", e.fillInStackTrace());
            }
            return MapperUtility.sourceToTarget(user, UserDto.class);
        } catch (Exception e) {
            log.error("Error during user creation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to register user.", e);
        }
    }



    @Override
    public UserDto find(String id) {
        log.info("Inside @class UserServiceImpl @method find @Param id :{}", id);
        if(id == null)
        {
            throw new IllegalCallerException("User id can not be null");
        }
        try {
            User user = this.userRepository.findById(id).orElseThrow(RuntimeException::new);
            return MapperUtility.sourceToTarget(user, UserDto.class,"password");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<UserDto> findAll() {
        log.info("Inside @class UserServiceImpl @method findAll");
        try {
            List<User> all = this.userRepository.findAll();
            return  all.stream().map(user -> {
                try {
                    return MapperUtility.sourceToTarget(user, UserDto.class,"password");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional
    public UserDto update(UserDto userDto, String id) {
        log.info("Inside @class UserServiceImpl @method update @Param id :{}", id);
        if(id == null)
        {
            create(userDto);
        }
        try{
            User user = this.userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
            user.setFirstName(userDto.getFirstName());
            user.setMiddleName(userDto.getMiddleName());
            user.setLastName(userDto.getLastName());
            user.setUsername(userDto.getUsername());
            user.setEmail(userDto.getEmail());
            user.setPhoneNumber(userDto.getPhoneNumber());
            if (userDto.getPassword() != null && !userDto.getPassword().isBlank()) {
                user.setPassword(encoder.encode(userDto.getPassword()));
            }
            log.info("User with id {} updated successfully", id);
            User user1 = this.userRepository.saveAndFlush(user);
            return MapperUtility.sourceToTarget(user1, UserDto.class,"password");
        } catch (Exception e) {
            log.error("Error while updating user with id {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to update user", e);
        }
    }

    @Override
    public void delete(String id) {
        log.info("Inside @class UserServiceImpl @method delete @Param id :{}", id);
        if (id == null) {
            log.warn("User ID is null. Cannot delete user.");
            throw new IllegalArgumentException("User ID cannot be null");
        }
        try {
            // Step 1: Check if user exists
            Optional<User> existingUserOpt = userRepository.findById(id);
            if (existingUserOpt.isEmpty()) {
                log.warn("User with id {} not found. Nothing to delete.", id);
                throw new EntityNotFoundException("User not found with id: " + id);
            }
            userRepository.deleteById(id);
            log.info("User with id {} deleted successfully", id);
        } catch (Exception e) {
            log.error("Error while deleting user with id {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    @Override
    public UserDto findByUsername(String username) {
        log.info("Inside @class UserServiceImpl @method findByUsername @Param username :{}", username);
        User user = this.userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
        try {
            return MapperUtility.sourceToTarget(user, UserDto.class, "password");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest loginRequest) {
        log.info("Inside @class UserServiceImpl @method delete @Param loginRequest :{}", loginRequest);
        try {
            Authentication authentication = manager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );
            log.info("authentication");
            User userDetails = (User)authentication.getPrincipal();
            String username = userDetails.getUsername();
            String userRole = userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList().getFirst();
            log.info("role :{}",userRole);
            String jwtToken = jwtUtils.generateToken(username, userRole);
            String rawRefreshToken = issueRefreshToken(userDetails);
            log.info("User '{}' logged in successfully with role: {}", username, userRole);
            return LoginResponse.builder()
                    .username(userDetails.getUsername())
                    .role(userRole)
                    .token(jwtToken)
                    .refreshToken(rawRefreshToken)
                    .build();
        }catch (Exception e)
        {
            throw new RuntimeException("Login failed");
        }
    }

    @Override
    @Transactional
    public RefreshTokenResponse refreshAccessToken(RefreshTokenRequest request) {
        log.info("Inside @class UserServiceImpl @method refreshAccessToken");
        RefreshToken existing = refreshTokenRepository.findByTokenHash(jwtUtils.hashToken(request.getRefreshToken()))
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (existing.isRevoked()) {
            throw new RuntimeException("Refresh token has been revoked");
        }
        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refresh token has expired");
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