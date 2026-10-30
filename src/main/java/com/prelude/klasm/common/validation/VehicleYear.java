package com.prelude.klasm.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Model year between 1981 (first 17-character VINs) and next year. Null is valid. */
@Documented
@Constraint(validatedBy = VehicleYearValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface VehicleYear {

    String message() default "must be between 1981 and next year";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
