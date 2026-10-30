package com.prelude.klasm.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Exposes the trace id on every response so a client-reported error can be found in logs and traces. */
@Component
// Must run inside Spring Boot's ServerHttpObservationFilter (HIGHEST_PRECEDENCE + 1), which opens the span
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class TraceIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Trace-Id";

    private final CurrentTraceId currentTraceId;

    TraceIdFilter(CurrentTraceId currentTraceId) {
        this.currentTraceId = currentTraceId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = currentTraceId.get();
        if (traceId != null) {
            response.setHeader(HEADER, traceId);
        }
        chain.doFilter(request, response);
    }
}
