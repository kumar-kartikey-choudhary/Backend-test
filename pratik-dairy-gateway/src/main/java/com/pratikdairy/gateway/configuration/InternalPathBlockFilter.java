package com.pratikdairy.gateway.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class InternalPathBlockFilter implements GlobalFilter, Ordered {

    public static boolean isInternalPath(String path) {
        if (path == null) {
            return false;
        }
        for (String segment : path.split("/")) {
            if ("internal".equalsIgnoreCase(segment)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // getPath() is the decoded path, and normalize() folds "/./" and "/../", so "%69nternal" and
        // "/x/../internal" can't dodge the check.
        String path = exchange.getRequest().getURI().normalize().getPath();
        if (isInternalPath(path)) {
            log.warn("Blocked external request to internal path {} {}", exchange.getRequest().getMethod(), path);
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
