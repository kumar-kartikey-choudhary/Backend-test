package com.pratikdairy.user.config;


import com.pratikdairy.parent.security.JsonAuthenticationEntryPoint;
import com.pratikdairy.parent.security.InternalApiKeyFilter;
import com.pratikdairy.user.jwt.CustomUserDetailService;
import com.pratikdairy.user.jwt.JwtAuthFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    private final CustomUserDetailService detailService;
    private final JwtAuthFilter authFilter;
    private final InternalApiKeyFilter internalApiKeyFilter;

    @Autowired
    public SecurityConfig(CustomUserDetailService detailService, JwtAuthFilter authFilter,
                          InternalApiKeyFilter internalApiKeyFilter)
    {
        this.detailService = detailService;
        this.authFilter = authFilter;
        this.internalApiKeyFilter = internalApiKeyFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception
    {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/users/register" , "/users/login", "/users/refresh-token", "/users/forgot-password", "/users/reset-password", "/users/verify-email", "/user-docs").permitAll()
                        // The public contact form (the admin endpoints under /users/admin/contact stay locked).
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/users/contact").permitAll()
                        // Service-to-service only: creating a notification on behalf of a user.
                        .requestMatchers("/users/notifications/internal/**").hasRole("SERVICE")
                        .anyRequest().authenticated())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // 401 (not Spring's default 403) when there is no valid login, so the frontend can refresh the token.
                .exceptionHandling(e -> e.authenticationEntryPoint(new JsonAuthenticationEntryPoint()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(internalApiKeyFilter, JwtAuthFilter.class)
                .authenticationProvider(this.provider());

        return http.build();
    }

    @Bean
    public PasswordEncoder encoder()
    {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception
    {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider provider()
    {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(this.encoder());
        provider.setUserDetailsService(detailService);

        return provider;
    }

}