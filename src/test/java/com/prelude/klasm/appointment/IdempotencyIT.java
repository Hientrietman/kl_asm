package com.prelude.klasm.appointment;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prelude.klasm.ApiTestSupport;
import com.prelude.klasm.SeedData;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** FR-3.4 / ADR-010: Idempotency-Key replay, mismatch and concurrent retries. */
class IdempotencyIT extends ApiTestSupport {

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void sameKeyAndBodyReturnsOriginalAppointmentWithoutNewRow() {
        Map<String, Object> body = booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));
        String id = post(body, "key-1").statusCode(201).extract().path("id");

        post(body, "key-1").statusCode(200).body("id", equalTo(id)).body("status", equalTo("CONFIRMED"));

        assertThat(appointmentCount()).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentBodyReturnsMismatch() {
        post(booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1)), "key-1").statusCode(201);

        post(booking(SeedData.OIL_CHANGE, "2030-01-07T11:00:00Z", vin(1)), "key-1")
                .statusCode(422)
                .contentType("application/problem+json")
                .body("code", equalTo("IDEMPOTENCY_KEY_MISMATCH"));
        assertThat(appointmentCount()).isEqualTo(1);
    }

    @Test
    void normalizedDifferencesStillCountAsTheSameRequest() {
        Map<String, Object> body = booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));
        String id = post(body, "key-1").statusCode(201).extract().path("id");

        Map<String, Object> retry = booking(SeedData.OIL_CHANGE, "2030-01-07T10:00:00+01:00", vin(1).toLowerCase());
        @SuppressWarnings("unchecked")
        Map<String, Object> customer = (Map<String, Object>) retry.get("customer");
        customer.put("email", "  " + vin(1).toUpperCase() + "@EXAMPLE.COM ");
        customer.put("fullName", " Customer " + vin(1) + " ");

        post(retry, "key-1").statusCode(200).body("id", equalTo(id));
    }

    @Test
    void replayReturnsOriginalEvenWhenTimeRulesWouldNowReject() {
        Map<String, Object> body = booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1));
        String id = post(body, "key-1").statusCode(201).extract().path("id");

        clock.set(Instant.parse("2030-01-07T08:30:00Z")); // now inside the minimum lead time

        post(body, "key-1").statusCode(200).body("id", equalTo(id));
    }

    @Test
    void failedRequestIsNotStoredSoTheKeyCanBeReused() {
        post(booking(SeedData.OIL_CHANGE, "2030-01-06T10:00:00Z", vin(1)), "key-1") // Sunday, closed
                .statusCode(422)
                .body("code", equalTo("OUTSIDE_WORKING_HOURS"));

        post(booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1)), "key-1").statusCode(201);
    }

    @Test
    void keyLongerThan64CharactersIsRejected() {
        post(booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1)), "k".repeat(65))
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("errors.field", hasItem("idempotencyKey"));
    }

    @Test
    void concurrentRetriesWithSameKeyCreateExactlyOneAppointment() throws Exception {
        int requests = 10;
        String json = objectMapper.writeValueAsString(booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(1)));
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/appointments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "parallel-key")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        List<HttpResponse<String>> responses = new ArrayList<>();
        try {
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return client.send(request, HttpResponse.BodyHandlers.ofString());
                }));
            }
            start.countDown();
            for (Future<HttpResponse<String>> future : futures) {
                responses.add(future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(responses).filteredOn(r -> r.statusCode() == 201).hasSize(1);
        assertThat(responses).filteredOn(r -> r.statusCode() == 200).hasSize(requests - 1);
        List<String> ids = new ArrayList<>();
        for (HttpResponse<String> response : responses) {
            ids.add(objectMapper.readTree(response.body()).path("id").asText());
        }
        assertThat(ids).containsOnly(ids.get(0));
        assertThat(appointmentCount()).isEqualTo(1);
    }

    private ValidatableResponse post(Map<String, Object> body, String idempotencyKey) {
        return given().port(port)
                .contentType(ContentType.JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(body)
                .post("/api/v1/appointments")
                .then();
    }

    private long appointmentCount() {
        return jdbc.queryForObject("SELECT count(*) FROM appointment", Long.class);
    }
}
