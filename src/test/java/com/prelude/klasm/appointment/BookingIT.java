package com.prelude.klasm.appointment;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;

import com.prelude.klasm.ApiTestSupport;
import com.prelude.klasm.SeedData;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-1, FR-2, FR-3 through the API against PostgreSQL, one test per rule and error code. */
class BookingIT extends ApiTestSupport {

    @Test
    void autoAssignsFirstBayAndLeastLoadedTechnician() {
        String id = post(booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1)))
                .statusCode(201)
                .header("Location", startsWith("/api/v1/appointments/"))
                .body("status", equalTo("CONFIRMED"))
                .body("timeZone", equalTo("Europe/London"))
                .body("startTime", equalTo("2030-01-07T09:00:00Z"))
                .body("endTime", equalTo("2030-01-07T10:00:00Z"))
                .body("serviceBay.code", equalTo("BAY-01"))
                .body("technician.id", equalTo(SeedData.LONDON_TECH_1.toString()))
                .extract()
                .path("id");

        assertThat(jdbc.queryForObject("SELECT status FROM appointment WHERE id = ?::uuid", String.class, id))
                .isEqualTo("CONFIRMED");
    }

    @Test
    void prefersTechnicianWithFewestBookedMinutesThatDay() {
        post(booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1))).statusCode(201);

        // Tech 1 now has 60 booked minutes on Monday, so a free technician with 0 minutes wins
        post(booking(SeedData.OIL_CHANGE, "2030-01-07T11:00:00Z", vin(2)))
                .statusCode(201)
                .body("serviceBay.code", equalTo("BAY-01"))
                .body("technician.id", equalTo(SeedData.LONDON_TECH_2.toString()));
    }

    @Test
    void assignsAnotherBayAndTechnicianWhenFirstAreBusy() {
        post(booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1))).statusCode(201);

        post(booking(SeedData.OIL_CHANGE, "2030-01-07T09:30:00Z", vin(2)))
                .statusCode(201)
                .body("serviceBay.code", equalTo("BAY-02"))
                .body("technician.id", equalTo(SeedData.LONDON_TECH_2.toString()));
    }

    @Test
    void adjacentAppointmentsDoNotConflict() {
        Map<String, Object> first = booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));
        first.put("serviceBayId", SeedData.LONDON_BAY_1.toString());
        first.put("technicianId", SeedData.LONDON_TECH_1.toString());
        post(first).statusCode(201);

        Map<String, Object> next = booking(SeedData.OIL_CHANGE, "2030-01-07T10:00:00Z", vin(1));
        next.put("serviceBayId", SeedData.LONDON_BAY_1.toString());
        next.put("technicianId", SeedData.LONDON_TECH_1.toString());
        post(next).statusCode(201);
    }

    @Test
    void reusesExistingCustomerWithoutModifyingIt() {
        Map<String, Object> first = booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));
        customer(first).put("email", "jane@example.com");
        customer(first).put("fullName", "Jane Original");
        String customerId = post(first).statusCode(201).extract().path("customerId");

        Map<String, Object> second = booking(SeedData.OIL_CHANGE, "2030-01-07T13:00:00Z", vin(2));
        customer(second).put("email", "  JANE@example.com ");
        customer(second).put("fullName", "Jane Changed");
        post(second).statusCode(201).body("customerId", equalTo(customerId));

        assertThat(jdbc.queryForObject("SELECT full_name FROM customer WHERE email = 'jane@example.com'", String.class))
                .isEqualTo("Jane Original");
    }

    @Test
    void invalidVinReturnsValidationError() {
        Map<String, Object> body = booking(SeedData.OIL_CHANGE, MONDAY_9AM, "1HGCM82633A00435I");

        assertProblem(post(body), 400, "VALIDATION_ERROR").body("errors.field", hasItem("vehicle.vin"));
    }

    @Test
    void vehicleYearIsCheckedAgainstInjectedClock() {
        Map<String, Object> body = booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));
        vehicle(body).put("year", 2032); // clock is in 2030, so 2031 is the latest allowed year

        assertProblem(post(body), 400, "VALIDATION_ERROR").body("errors.field", hasItem("vehicle.year"));
    }

    @Test
    void startOffTheGridReturnsValidationError() {
        assertProblem(post(booking(SeedData.OIL_CHANGE, "2030-01-07T09:10:00Z", vin(1))), 400, "VALIDATION_ERROR")
                .body("errors.field", hasItem("startTime"));
    }

    @Test
    void unknownDealershipReturnsNotFound() {
        Map<String, Object> body = booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));
        body.put("dealershipId", UUID.randomUUID().toString());

        assertProblem(post(body), 404, "NOT_FOUND");
    }

    @Test
    void unknownServiceTypeReturnsNotFound() {
        assertProblem(post(booking(UUID.randomUUID(), MONDAY_9AM, vin(1))), 404, "NOT_FOUND");
    }

    @Test
    void unknownExplicitBayReturnsNotFound() {
        Map<String, Object> body = booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));
        body.put("serviceBayId", UUID.randomUUID().toString());

        assertProblem(post(body), 404, "NOT_FOUND");
    }

    @Test
    void startInsideMinimumLeadTimeReturnsInvalidBookingWindow() {
        clock.set(Instant.parse("2030-01-07T08:30:00Z"));

        assertProblem(post(booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1))), 422, "INVALID_BOOKING_WINDOW");
    }

    @Test
    void startBeyondMaximumHorizonReturnsInvalidBookingWindow() {
        assertProblem(
                post(booking(SeedData.OIL_CHANGE, "2030-06-03T09:00:00+01:00", vin(1))), 422, "INVALID_BOOKING_WINDOW");
    }

    @Test
    void closedDayReturnsOutsideWorkingHours() {
        assertProblem(post(booking(SeedData.OIL_CHANGE, "2030-01-06T10:00:00Z", vin(1))), 422, "OUTSIDE_WORKING_HOURS");
    }

    @Test
    void serviceEndingAfterClosingReturnsOutsideWorkingHours() {
        // Brake service takes 120 minutes: 15:30 + 2h = 17:30, after the 17:00 closing
        assertProblem(
                post(booking(SeedData.BRAKE_SERVICE, "2030-01-07T15:30:00Z", vin(1))), 422, "OUTSIDE_WORKING_HOURS");
    }

    @Test
    void unqualifiedExplicitTechnicianReturnsTechnicianNotQualified() {
        Map<String, Object> body = booking(SeedData.FULL_INSPECTION, MONDAY_9AM, vin(1));
        body.put("technicianId", SeedData.LONDON_TECH_2.toString());

        assertProblem(post(body), 422, "TECHNICIAN_NOT_QUALIFIED");
    }

    @Test
    void bayOfAnotherDealershipReturnsResourceDealershipMismatch() {
        Map<String, Object> body = booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));
        body.put("serviceBayId", "b2000000-0000-0000-0000-000000000001");

        assertProblem(post(body), 422, "RESOURCE_DEALERSHIP_MISMATCH");
    }

    @Test
    void occupiedExplicitBayReturnsResourceConflict() {
        post(booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1))).statusCode(201);

        Map<String, Object> body = booking(SeedData.OIL_CHANGE, "2030-01-07T09:30:00Z", vin(2));
        body.put("serviceBayId", SeedData.LONDON_BAY_1.toString());

        assertProblem(post(body), 409, "RESOURCE_CONFLICT").body("conflict.resource", equalTo("SERVICE_BAY"));
    }

    @Test
    void overlappingBookingForSameVehicleReturnsVehicleAlreadyBooked() {
        post(booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1))).statusCode(201);

        assertProblem(
                        post(booking(SeedData.TIRE_ROTATION, "2030-01-07T09:30:00Z", vin(1))),
                        409,
                        "VEHICLE_ALREADY_BOOKED")
                .body("conflict.resource", equalTo("VEHICLE"));
    }

    @Test
    void noFreeQualifiedTechnicianReturnsNoAvailability() {
        // Only technician 1 can do a full inspection
        post(booking(SeedData.FULL_INSPECTION, MONDAY_9AM, vin(1))).statusCode(201);

        assertProblem(post(booking(SeedData.FULL_INSPECTION, "2030-01-07T10:00:00Z", vin(2))), 409, "NO_AVAILABILITY")
                .body("conflict.resource", equalTo("TECHNICIAN"));
    }

    private ValidatableResponse post(Map<String, Object> body) {
        Response response =
                given().port(port).contentType(ContentType.JSON).body(body).post("/api/v1/appointments");
        return response.then();
    }

    private static ValidatableResponse assertProblem(ValidatableResponse response, int status, String code) {
        return response.statusCode(status)
                .contentType("application/problem+json")
                .body("code", equalTo(code));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> customer(Map<String, Object> body) {
        return (Map<String, Object>) body.get("customer");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> vehicle(Map<String, Object> body) {
        return (Map<String, Object>) body.get("vehicle");
    }
}
