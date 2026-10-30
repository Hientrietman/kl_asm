package com.prelude.klasm.common.error;

/** A domain or request error that maps to one catalog code. The message becomes the Problem Details "detail". */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
