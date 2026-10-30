package com.prelude.klasm.common.error;

/** One invalid input value, reported in the "errors" member of a 400 response. */
public record FieldViolation(String field, String message) {}
