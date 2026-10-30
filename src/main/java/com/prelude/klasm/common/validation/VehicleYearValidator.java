package com.prelude.klasm.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Clock;
import java.time.Year;

/** Instantiated by Spring's constraint validator factory, so the {@link Clock} bean is injected. */
public class VehicleYearValidator implements ConstraintValidator<VehicleYear, Integer> {

    static final int FIRST_YEAR = 1981;

    private final Clock clock;

    public VehicleYearValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || (value >= FIRST_YEAR && value <= Year.now(clock).getValue() + 1);
    }
}
