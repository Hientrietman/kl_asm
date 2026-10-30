package com.prelude.klasm.appointment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import com.prelude.klasm.ApiTestSupport;
import com.prelude.klasm.SeedData;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-4: get, list with filters and paging, cancel. */
class AppointmentManagementIT extends ApiTestSupport {

    @Test
    void getsAppointmentById() {
        String id = book(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));

        given().port(port)
                .get("/api/v1/appointments/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("status", equalTo("CONFIRMED"))
                .body("timeZone", equalTo("Europe/London"))
                .body("serviceBay.code", equalTo("BAY-01"))
                .body("technician.fullName", equalTo("Alice Morgan"))
                .body("startTime", equalTo("2030-01-07T09:00:00Z"))
                .body("cancelledAt", nullValue());
    }

    @Test
    void unknownAppointmentReturnsNotFound() {
        given().port(port)
                .get("/api/v1/appointments/{id}", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void listsByStartTimeWithPaging() {
        String third = book(SeedData.OIL_CHANGE, "2030-01-07T13:00:00Z", vin(3));
        String first = book(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));
        String second = book(SeedData.OIL_CHANGE, "2030-01-07T11:00:00Z", vin(2));

        list(Map.of("size", 2))
                .statusCode(200)
                .body("content.id", contains(first, second))
                .body("page", equalTo(0))
                .body("size", equalTo(2))
                .body("totalElements", equalTo(3))
                .body("totalPages", equalTo(2));
        list(Map.of("size", 2, "page", 1)).statusCode(200).body("content.id", contains(third));
    }

    @Test
    void filtersByDealershipTimeRangeStatusAndVin() {
        String london = book(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));
        String londonLater = book(SeedData.OIL_CHANGE, "2030-01-08T09:00:00Z", vin(2));
        Map<String, Object> hcm = booking(SeedData.OIL_CHANGE, "2030-01-07T02:00:00Z", vin(3)); // 09:00 local
        hcm.put("dealershipId", SeedData.HO_CHI_MINH.toString());
        String hoChiMinh = post(hcm).statusCode(201).extract().path("id");
        cancel(londonLater, Map.of()).statusCode(200);

        list(Map.of("dealershipId", SeedData.HO_CHI_MINH)).body("content.id", contains(hoChiMinh));
        list(Map.of("from", "2030-01-07T09:00:00Z", "to", "2030-01-08T09:00:00Z"))
                .body("content.id", contains(london)); // from inclusive, to exclusive
        list(Map.of("status", "CANCELLED")).body("content.id", contains(londonLater));
        list(Map.of("vin", vin(3).toLowerCase())).body("content.id", contains(hoChiMinh));
        list(Map.of("vin", "1HGCM82633A004399")).body("content", hasSize(0)).body("totalElements", equalTo(0));
    }

    @Test
    void rejectsInvalidPagingAndStatus() {
        list(Map.of("size", 101)).statusCode(400).body("errors.field", hasItem("size"));
        list(Map.of("page", -1)).statusCode(400).body("errors.field", hasItem("page"));
        list(Map.of("status", "PENDING")).statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void cancelsAppointmentAndRecordsReason() {
        String id = book(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));

        cancel(id, Map.of("reason", "Customer called"))
                .statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("cancellationReason", equalTo("Customer called"))
                .body("cancelledAt", equalTo("2030-01-01T00:00:00Z"));
    }

    @Test
    void cancellingTwiceIsIdempotent() {
        String id = book(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));
        cancel(id, Map.of("reason", "First")).statusCode(200);
        clock.set(Instant.parse("2030-01-02T00:00:00Z"));

        cancel(id, Map.of("reason", "Second"))
                .statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("cancellationReason", equalTo("First"))
                .body("cancelledAt", equalTo("2030-01-01T00:00:00Z"));
    }

    @Test
    void cancelWithEmptyBodyIsAllowed() {
        String id = book(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));

        given().port(port)
                .contentType(ContentType.JSON)
                .post("/api/v1/appointments/{id}/cancel", id)
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("cancellationReason", nullValue());
    }

    @Test
    void cancellingFreesTheSlotImmediately() {
        Map<String, Object> body = booking(SeedData.FULL_INSPECTION, MONDAY_9AM, vin(1));
        String id = post(body).statusCode(201).extract().path("id");
        post(booking(SeedData.FULL_INSPECTION, MONDAY_9AM, vin(2))).statusCode(409);

        cancel(id, Map.of()).statusCode(200);

        post(booking(SeedData.FULL_INSPECTION, MONDAY_9AM, vin(2))).statusCode(201);
    }

    @Test
    void startedAppointmentIsNotCancellable() {
        String id = book(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));
        clock.set(Instant.parse(MONDAY_9AM));

        cancel(id, Map.of())
                .statusCode(422)
                .contentType("application/problem+json")
                .body("code", equalTo("APPOINTMENT_NOT_CANCELLABLE"));
    }

    @Test
    void cancelValidatesReasonAndId() {
        String id = book(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));

        cancel(id, Map.of("reason", "r".repeat(501))).statusCode(400).body("errors.field", hasItem("reason"));
        cancel(UUID.randomUUID().toString(), Map.of()).statusCode(404).body("code", equalTo("NOT_FOUND"));
    }

    private String book(UUID serviceTypeId, String start, String vin) {
        return post(booking(serviceTypeId, start, vin))
                .statusCode(201)
                .extract()
                .path("id");
    }

    private ValidatableResponse post(Map<String, Object> body) {
        return given().port(port)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/appointments")
                .then();
    }

    private ValidatableResponse list(Map<String, ?> params) {
        return given().port(port)
                .queryParams(params)
                .get("/api/v1/appointments")
                .then();
    }

    private ValidatableResponse cancel(String id, Map<String, Object> body) {
        return given().port(port)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/v1/appointments/{id}/cancel", id)
                .then();
    }
}
