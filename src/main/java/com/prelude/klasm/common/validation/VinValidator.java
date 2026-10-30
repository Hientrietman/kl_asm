package com.prelude.klasm.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class VinValidator implements ConstraintValidator<Vin, String> {

    private static final Pattern VIN = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || VIN.matcher(value).matches();
    }
}
