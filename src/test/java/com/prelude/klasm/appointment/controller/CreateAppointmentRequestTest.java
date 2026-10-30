package com.prelude.klasm.appointment.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.prelude.klasm.common.validation.VehicleYearValidator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Field rules of FR-1 without Spring: normalization and every constraint on the request. */
class CreateAppointmentRequestTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime START = OffsetDateTime.parse("2030-01-07T09:00:00Z");

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        var configuration = Validation.byDefaultProvider().configure();
        ConstraintValidatorFactory defaults = configuration.getDefaultConstraintValidatorFactory();
        factory = configuration
                .constraintValidatorFactory(new ConstraintValidatorFactory() {
                    @Override
                    public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
                        return key == VehicleYearValidator.class
                                ? key.cast(new VehicleYearValidator(CLOCK))
                                : defaults.getInstance(key);
                    }

                    @Override
                    public void releaseInstance(ConstraintValidator<?, ?> instance) {
                        defaults.releaseInstance(instance);
                    }
                })
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    @Test
    void validRequestHasNoViolations() {
        assertThat(validator.validate(request(customer(), vehicle(), START, null)))
                .isEmpty();
    }

    @Test
    void normalizesCustomerAndVehicleValues() {
        var customer =
                new CreateAppointmentRequest.Customer("  Jane Doe ", "  Jane.Doe@Example.COM ", " +447911123456 ");
        var vehicle = new CreateAppointmentRequest.Vehicle(" 1hgcm82633a004352 ", " Honda ", " Accord ", 2020);

        assertThat(customer.fullName()).isEqualTo("Jane Doe");
        assertThat(customer.email()).isEqualTo("jane.doe@example.com");
        assertThat(customer.phone()).isEqualTo("+447911123456");
        assertThat(vehicle.vin()).isEqualTo("1HGCM82633A004352");
        assertThat(vehicle.make()).isEqualTo("Honda");
        assertThat(validator.validate(request(customer, vehicle, START, null))).isEmpty();
    }

    @Test
    void requiresTopLevelFields() {
        var request = new CreateAppointmentRequest(null, null, null, null, null, null, null, null);

        assertThat(violatedPaths(request))
                .containsExactlyInAnyOrder("dealershipId", "serviceTypeId", "startTime", "customer", "vehicle");
    }

    @Test
    void rejectsStartOffTheGrid() {
        assertThat(violatedPaths(request(customer(), vehicle(), START.plusMinutes(10), null)))
                .containsExactly("startTime");
    }

    @Test
    void rejectsInvalidFullName() {
        assertViolation(new CreateAppointmentRequest.Customer("   ", "a@example.com", null), "customer.fullName");
        assertViolation(
                new CreateAppointmentRequest.Customer("x".repeat(101), "a@example.com", null), "customer.fullName");
        assertViolation(
                new CreateAppointmentRequest.Customer("Jane\u0000Doe", "a@example.com", null), "customer.fullName");
    }

    @Test
    void acceptsFullNameOfExactlyMaximumLength() {
        var customer = new CreateAppointmentRequest.Customer("x".repeat(100), "a@example.com", null);

        assertThat(validator.validate(request(customer, vehicle(), START, null)))
                .isEmpty();
    }

    @Test
    void rejectsInvalidEmail() {
        assertViolation(new CreateAppointmentRequest.Customer("Jane", "not-an-email", null), "customer.email");
        assertViolation(new CreateAppointmentRequest.Customer("Jane", null, null), "customer.email");
        assertViolation(
                new CreateAppointmentRequest.Customer("Jane", "a".repeat(64) + "@" + "b".repeat(186) + ".com", null),
                "customer.email");
    }

    @Test
    void rejectsPhoneNotInE164() {
        assertViolation(
                new CreateAppointmentRequest.Customer("Jane", "a@example.com", "07911123456"), "customer.phone");
        assertViolation(
                new CreateAppointmentRequest.Customer("Jane", "a@example.com", "+0123456789"), "customer.phone");
        assertViolation(new CreateAppointmentRequest.Customer("Jane", "a@example.com", "+12345"), "customer.phone");
    }

    @Test
    void rejectsInvalidVehicle() {
        assertVehicleViolation(new CreateAppointmentRequest.Vehicle(null, null, null, null), "vehicle.vin");
        assertVehicleViolation(
                new CreateAppointmentRequest.Vehicle("1HGCM82633A00435I", null, null, null), "vehicle.vin");
        assertVehicleViolation(
                new CreateAppointmentRequest.Vehicle("1HGCM82633A004352", "m".repeat(51), null, null), "vehicle.make");
        assertVehicleViolation(
                new CreateAppointmentRequest.Vehicle("1HGCM82633A004352", null, "m".repeat(51), null), "vehicle.model");
        assertVehicleViolation(
                new CreateAppointmentRequest.Vehicle("1HGCM82633A004352", null, null, 1980), "vehicle.year");
        assertVehicleViolation(
                new CreateAppointmentRequest.Vehicle("1HGCM82633A004352", null, null, 2032), "vehicle.year");
    }

    @Test
    void rejectsNotesLongerThan500Characters() {
        assertThat(violatedPaths(request(customer(), vehicle(), START, "n".repeat(501))))
                .containsExactly("notes");
    }

    private void assertViolation(CreateAppointmentRequest.Customer customer, String path) {
        assertThat(violatedPaths(request(customer, vehicle(), START, null))).containsExactly(path);
    }

    private void assertVehicleViolation(CreateAppointmentRequest.Vehicle vehicle, String path) {
        assertThat(violatedPaths(request(customer(), vehicle, START, null))).containsExactly(path);
    }

    private Set<String> violatedPaths(CreateAppointmentRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static CreateAppointmentRequest request(
            CreateAppointmentRequest.Customer customer,
            CreateAppointmentRequest.Vehicle vehicle,
            OffsetDateTime start,
            String notes) {
        return new CreateAppointmentRequest(
                UUID.randomUUID(), UUID.randomUUID(), start, customer, vehicle, null, null, notes);
    }

    private static CreateAppointmentRequest.Customer customer() {
        return new CreateAppointmentRequest.Customer("Jane Doe", "jane@example.com", "+447911123456");
    }

    private static CreateAppointmentRequest.Vehicle vehicle() {
        return new CreateAppointmentRequest.Vehicle("1HGCM82633A004352", "Honda", "Accord", 2020);
    }
}
