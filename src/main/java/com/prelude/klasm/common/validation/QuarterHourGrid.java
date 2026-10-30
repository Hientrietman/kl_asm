package com.prelude.klasm.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The instant lies on the 15-minute booking grid with zero seconds and fractions (A-4). Null is valid. */
@Documented
@Constraint(validatedBy = QuarterHourGridValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface QuarterHourGrid {

    String message() default "must be on a 15-minute boundary (minutes 00, 15, 30 or 45, no seconds)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
