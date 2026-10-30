package com.prelude.klasm.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 17 characters from {@code [A-HJ-NPR-Z0-9]}; the check digit is not verified (A-13). Null is valid. */
@Documented
@Constraint(validatedBy = VinValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Vin {

    String message() default "must be 17 characters: digits and letters except I, O and Q";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
