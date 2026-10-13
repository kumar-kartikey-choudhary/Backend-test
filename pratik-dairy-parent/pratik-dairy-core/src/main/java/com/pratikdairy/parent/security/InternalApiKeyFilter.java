package com.pratikdairy.parent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Guards the service-to-service ("internal") endpoints - any path with an {@code internal} segment,
 * such as {@code /products/internal/{id}/decrement-stock}.
 *
 * Those endpoints used to accept any logged-in customer's JWT, because the calling service simply
 * forwarded the customer's token. Now a request to an internal path must carry the shared
 * {@value #HEADER} header. If it does, the caller is authenticated as the {@code internal-service}
 * principal with {@value #SERVICE_ROLE} (replacing whatever JWT identity it carried); if not, it is
 * rejected with 403 whoever it claims to be. Each service's security chain then requires
 * {@value #SERVICE_ROLE} for its internal URLs, so a service that forgets to register this filter
 * fails closed rather than open.
 *
 * Calling services add the header with their Feign interceptor; the gateway never forwards internal
 * paths from outside at all.
 */
@Slf4j
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Key";
    public static final String SERVICE_ROLE = "ROLE_SERVICE";
    public static final String SERVICE_PRINCIPAL = "internal-service";

    /** Only for local development - set INTERNAL_API_KEY (or internal.api-key) everywhere else. */
    public static final String DEFAULT_KEY = "dev-internal-key-change-me";
    /** Property placeholder shared by this filter and the Feign interceptors so they always agree. */
    public static final String KEY_PLACEHOLDER = "${internal.api-key:${INTERNAL_API_KEY:" + DEFAULT_KEY + "}}";

    private final byte[] expectedKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalApiKeyFilter(@Value(KEY_PLACEHOLDER) String expectedKey) {
        this.expectedKey = expectedKey == null ? new byte[0] : expectedKey.getBytes(StandardCharsets.UTF_8);
        if (DEFAULT_KEY.equals(expectedKey)) {
            log.warn("Internal API key is the built-in development default - set INTERNAL_API_KEY before deploying.");
        } else if (this.expectedKey.length == 0) {
            log.warn("Internal API key is blank - every internal endpoint will be rejected.");
        }
    }

    /** True if any segment of the path is {@code internal} (case-insensitive, query string ignored). */
    public static boolean isInternalPath(String path) {
        if (path == null) {
            return false;
        }
        int query = path.indexOf('?');
        String pathOnly = query >= 0 ? path.substring(0, query) : path;
        for (String segment : pathOnly.split("/")) {
            if ("internal".equalsIgnoreCase(segment)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isInternalPath(decodedPath(request))) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!keyMatches(request.getHeader(HEADER))) {
            log.warn("Rejected {} {} - internal endpoint called without a valid {}",
                    request.getMethod(), request.getRequestURI(), HEADER);
            reject(request, response);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                SERVICE_PRINCIPAL, null, List.of(new SimpleGrantedAuthority(SERVICE_ROLE))));
        filterChain.doFilter(request, response);
    }

    // Constant-time, and never true when no key is configured.
    private boolean keyMatches(String supplied) {
        if (supplied == null || expectedKey.length == 0) {
            return false;
        }
        return MessageDigest.isEqual(expectedKey, supplied.getBytes(StandardCharsets.UTF_8));
    }

    // The decoded, normalised path the servlet container will route on - so "%69nternal" or
    // "/./internal" can't slip past a check on the raw URI.
    private static String decodedPath(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            String uri = request.getRequestURI();
            try {
                path = uri == null ? "" : UriUtils.decode(uri, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                path = uri; // malformed escape: the raw form still gets checked
            }
        }
        if (request.getPathInfo() != null) {
            path += request.getPathInfo();
        }
        return path;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", HttpStatus.FORBIDDEN.getReasonPhrase());
        body.put("code", "FORBIDDEN");
        body.put("message", "This endpoint is only available to internal services.");
        body.put("path", request.getRequestURI());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
