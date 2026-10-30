package com.prelude.klasm.availability;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import com.prelude.klasm.ApiTestSupport;
import com.prelude.klasm.SeedData;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-5: the availability query agrees with what booking actually accepts. */
class AvailabilityIT extends ApiTestSupport {

    private static final String MONDAY = "2030-01-07";

    @Test
    void listsEveryQuarterHourStartOfAnOpenDay() {
        // Oil change takes 60 minutes, London opens 08:00-17:00: 08:00 ... 16:00
        availability(SeedData.LONDON, SeedData.OIL_CHANGE, MONDAY)
                .statusCode(200)
                .body("timeZone", equalTo("Europe/London"))
                .body("durationMinutes", equalTo(60))
                .body("slots", hasSize(33))
                .body("slots[0].startTime", equalTo("2030-01-07T08:00:00Z"))
                .body("slots[32].startTime", equalTo("2030-01-07T16:00:00Z"));
    }

    @Test
    void returnsLocalAndUtcTimesInTheDealershipZone() {
        availability(SeedData.HO_CHI_MINH, SeedData.OIL_CHANGE, MONDAY)
                .statusCode(200)
                .body("slots[0].startTime", equalTo("2030-01-07T01:00:00Z"))
                .body("slots[0].localStartTime", equalTo("2030-01-07T08:00:00+07:00"));
    }

    @Test
    void closedDayHasNoSlots() {
        availability(SeedData.LONDON, SeedData.OIL_CHANGE, "2030-01-06")
                .statusCode(200)
                .body("slots", hasSize(0));
    }

    @Test
    void excludesStartsInsideTheMinimumLeadTime() {
        clock.set(Instant.parse("2030-01-07T10:05:00Z")); // earliest bookable start is 11:05

        availability(SeedData.LONDON, SeedData.OIL_CHANGE, MONDAY)
                .statusCode(200)
                .body("slots[0].startTime", equalTo("2030-01-07T11:15:00Z"));
    }

    @Test
    void bookedSlotsDisappearAndResultMatchesBooking() {
        // Only technician 1 can do a 180-minute full inspection; booking 09:00-12:00 blocks starts 08:00-11:45
        post(booking(SeedData.FULL_INSPECTION, MONDAY_9AM, vin(1))).statusCode(201);

        List<String> starts = availability(SeedData.LONDON, SeedData.FULL_INSPECTION, MONDAY)
                .statusCode(200)
                .extract()
                .path("slots.startTime");
        assertThat(starts)
                .containsExactly(
                        "2030-01-07T12:00:00Z",
                        "2030-01-07T12:15:00Z",
                        "2030-01-07T12:30:00Z",
                        "2030-01-07T12:45:00Z",
                        "2030-01-07T13:00:00Z",
                        "2030-01-07T13:15:00Z",
                        "2030-01-07T13:30:00Z",
                        "2030-01-07T13:45:00Z",
                        "2030-01-07T14:00:00Z");

        post(booking(SeedData.FULL_INSPECTION, "2030-01-07T10:00:00Z", vin(2))).statusCode(409);
        post(booking(SeedData.FULL_INSPECTION, starts.get(0), vin(3))).statusCode(201);
    }

    @Test
    void noAvailabilitySuggestsClosestAlternativesOnTheSameDay() {
        post(booking(SeedData.FULL_INSPECTION, MONDAY_9AM, vin(1))).statusCode(201);

        post(booking(SeedData.FULL_INSPECTION, "2030-01-07T10:00:00Z", vin(2)))
                .statusCode(409)
                .body("code", equalTo("NO_AVAILABILITY"))
                .body(
                        "conflict.alternatives",
                        contains("2030-01-07T12:00:00Z", "2030-01-07T12:15:00Z", "2030-01-07T12:30:00Z"));
    }

    @Test
    void validatesDealershipServiceTypeAndDate() {
        availability(UUID.randomUUID(), SeedData.OIL_CHANGE, MONDAY)
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
        availability(SeedData.LONDON, UUID.randomUUID(), MONDAY).statusCode(404).body("code", equalTo("NOT_FOUND"));
        availability(SeedData.LONDON, SeedData.OIL_CHANGE, "07-01-2030")
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
        given().port(port)
                .queryParam("serviceTypeId", SeedData.OIL_CHANGE)
                .get("/api/v1/dealerships/{id}/availability", SeedData.LONDON)
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    private ValidatableResponse availability(UUID dealershipId, UUID serviceTypeId, String date) {
        return given().port(port)
                .queryParam("serviceTypeId", serviceTypeId)
                .queryParam("date", date)
                .get("/api/v1/dealerships/{id}/availability", dealershipId)
                .then();
    }

    private ValidatableResponse post(Map<String, Object> body) {
        return given().port(port)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/appointments")
                .then();
    }
}
