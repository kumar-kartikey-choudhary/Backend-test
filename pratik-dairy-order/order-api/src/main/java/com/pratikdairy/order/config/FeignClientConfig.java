package com.pratikdairy.order.config;

import com.pratikdairy.parent.security.InternalApiKeyFilter;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor authHeaderInterceptor(@Value(InternalApiKeyFilter.KEY_PLACEHOLDER) String internalApiKey) {
        return (RequestTemplate template) -> {
            // Calls to another service's /internal/ endpoints prove they come from a service, not a
            // customer, with the shared key (the customer JWT below is not enough for those).
            if (InternalApiKeyFilter.isInternalPath(template.url())) {
                template.header(InternalApiKeyFilter.HEADER, internalApiKey);
            }
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null) {
                    template.header("Authorization", authHeader);
                }
            }
        };
    }
}