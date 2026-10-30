package com.prelude.klasm.appointment.controller;

import com.prelude.klasm.common.validation.QuarterHourGrid;
import com.prelude.klasm.common.validation.VehicleYear;
import com.prelude.klasm.common.validation.Vin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/** Body of {@code POST /api/v1/appointments} (FR-1). Values are normalized before validation runs. */
public record CreateAppointmentRequest(
        @NotNull UUID dealershipId,
        @NotNull UUID serviceTypeId,
        @NotNull @QuarterHourGrid OffsetDateTime startTime,
        @NotNull @Valid Customer customer,
        @NotNull @Valid Vehicle vehicle,
        UUID serviceBayId,
        UUID technicianId,
        @Size(max = 500) String notes) {

    static final String NO_CONTROL_CHARACTERS = "^[^\\p{Cc}]*$";

    public record Customer(
            @NotBlank
            @Size(max = 100)
            @Pattern(regexp = NO_CONTROL_CHARACTERS, message = "must not contain control characters")
            String fullName,

            @NotBlank @Email @Size(max = 254) String email,

            @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "must be in E.164 format, for example +447911123456")
            String phone) {

        public Customer {
            fullName = trim(fullName);
            email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
            phone = trim(phone);
        }
    }

    public record Vehicle(
            @NotNull @Vin String vin,
            @Size(max = 50) String make,
            @Size(max = 50) String model,
            @VehicleYear Integer year) {

        public Vehicle {
            vin = vin == null ? null : vin.trim().toUpperCase(Locale.ROOT);
            make = trim(make);
            model = trim(model);
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
