package com.prelude.klasm.common.error;

import java.util.Map;

/** A domain or request error that maps to one catalog code. The message becomes the Problem Details "detail". */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> properties;

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, Map.of());
    }

    /** {@code properties} become extension members of the Problem Details body, for example {@code conflict}. */
    public ApiException(ErrorCode code, String detail, Map<String, Object> properties) {
        super(detail);
        this.code = code;
        this.properties = Map.copyOf(properties);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
