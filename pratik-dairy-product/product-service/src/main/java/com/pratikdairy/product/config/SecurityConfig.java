package com.pratikdairy.product.config;


import com.pratikdairy.parent.security.JsonAuthenticationEntryPoint;
import com.pratikdairy.parent.security.InternalApiKeyFilter;
import com.pratikdairy.parent.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Autowired
    private InternalApiKeyFilter internalApiKeyFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Service-to-service only: InternalApiKeyFilter grants ROLE_SERVICE to callers holding the
                        // internal key. A customer's JWT, even an admin's, is not enough.
                        .requestMatchers("/products/internal/**").hasRole("SERVICE")
                        .requestMatchers(
                                "/products/all",
                                "/products/search",
                                "/products/product/*",
                                "/products/category/*",
                                "/products/images/*",
                                "/products/ratings"
                        ).permitAll()
                        // Reading a product's reviews is public; writing one needs a login.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/products/product/*/reviews").permitAll()
                        .anyRequest().authenticated()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // 401 (not Spring's default 403) when there is no valid login, so the frontend can refresh the token.
                .exceptionHandling(e -> e.authenticationEntryPoint(new JsonAuthenticationEntryPoint()))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(internalApiKeyFilter, JwtAuthFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}