package com.prelude.klasm.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prelude.klasm.ApiTestSupport;
import com.prelude.klasm.SeedData;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** NFR-1: parallel requests for the same slot produce exactly one booking; the database decides the winner. */
class BookingConcurrencyIT extends ApiTestSupport {

    private static final int REQUESTS = 20;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void sameExplicitBayAndTechnicianIsBookedExactlyOnce() throws Exception {
        List<HttpResponse<String>> responses = fireInParallel(n -> {
            Map<String, Object> body = booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(n));
            body.put("serviceBayId", SeedData.LONDON_BAY_1.toString());
            body.put("technicianId", SeedData.LONDON_TECH_1.toString());
            return body;
        });

        assertExactlyOneCreated(responses, "RESOURCE_CONFLICT");
    }

    @Test
    void autoAssignmentWithSingleQualifiedTechnicianIsBookedExactlyOnce() throws Exception {
        // Only technician 1 can do a full inspection, so all requests compete for one technician
        List<HttpResponse<String>> responses =
                fireInParallel(n -> booking(SeedData.FULL_INSPECTION, MONDAY_9AM, vin(n)));

        assertExactlyOneCreated(responses, "NO_AVAILABILITY");
    }

    private void assertExactlyOneCreated(List<HttpResponse<String>> responses, String conflictCode) throws Exception {
        assertThat(responses).noneMatch(response -> response.statusCode() >= 500);
        assertThat(responses).filteredOn(r -> r.statusCode() == 201).hasSize(1);
        assertThat(responses).filteredOn(r -> r.statusCode() == 409).hasSize(REQUESTS - 1);
        for (HttpResponse<String> response : responses) {
            if (response.statusCode() == 409) {
                JsonNode problem = objectMapper.readTree(response.body());
                assertThat(problem.path("code").asText()).isEqualTo(conflictCode);
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointment WHERE status = 'CONFIRMED'", Long.class))
                .isEqualTo(1L);
    }

    private List<HttpResponse<String>> fireInParallel(IntFunction<Map<String, Object>> bodyForRequest)
            throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create("http://localhost:" + port + "/api/v1/appointments");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(REQUESTS);
        try {
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (int n = 0; n < REQUESTS; n++) {
                String json = objectMapper.writeValueAsString(bodyForRequest.apply(n));
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                futures.add(executor.submit(() -> {
                    start.await();
                    return client.send(request, HttpResponse.BodyHandlers.ofString());
                }));
            }
            start.countDown();
            List<HttpResponse<String>> responses = new ArrayList<>();
            for (Future<HttpResponse<String>> future : futures) {
                responses.add(future.get());
            }
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }
}
