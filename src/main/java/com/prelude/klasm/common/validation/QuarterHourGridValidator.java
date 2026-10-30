package com.prelude.klasm.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Instant;
import java.time.OffsetDateTime;

public class QuarterHourGridValidator implements ConstraintValidator<QuarterHourGrid, OffsetDateTime> {

    private static final long GRID_SECONDS = 15 * 60;

    @Override
    public boolean isValid(OffsetDateTime value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        // Checked on the UTC instant so the result does not depend on the offset the client chose
        Instant instant = value.toInstant();
        return instant.getNano() == 0 && Math.floorMod(instant.getEpochSecond(), GRID_SECONDS) == 0;
    }
}
