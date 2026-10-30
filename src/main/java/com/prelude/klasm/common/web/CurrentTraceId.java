package com.prelude.klasm.common.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

@Component
public class CurrentTraceId {

    private final Tracer tracer;

    public CurrentTraceId(Tracer tracer) {
        this.tracer = tracer;
    }

    /** Returns the trace id of the current span, or {@code null} when tracing is inactive. */
    public String get() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        String traceId = span.context().traceId();
        return traceId == null || traceId.isBlank() ? null : traceId;
    }
}
