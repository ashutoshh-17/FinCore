package com.bankflow.common.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that extracts (or generates) a {@code X-Correlation-Id} header and
 * stores it in the SLF4J MDC under the key {@code correlationId} for the duration of
 * the request.
 *
 * <p>The header is also echoed back in the response so callers can trace their request.
 * Ordering is set to {@code HIGHEST_PRECEDENCE} so all downstream filters and handlers
 * already have access to the correlation id via {@code MDC.get("correlationId")}.
 *
 * <p>Usage in services: import this class and register it as a {@code @Bean} in any
 * service that does not use Spring Cloud Gateway's built-in correlation id filter.
 */
@Component
@Order(Integer.MIN_VALUE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
