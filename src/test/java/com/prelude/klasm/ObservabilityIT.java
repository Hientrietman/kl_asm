package com.prelude.klasm;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.http.ContentType;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** NFR-3: custom metrics are scraped by Prometheus and booking logs contain no personal data. */
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIT extends ApiTestSupport {

    @Test
    void exposesBookingMetricsForPrometheus() {
        double bookedBefore = metricValue("appointments_booked_total");
        double noAvailabilityBefore = metricValue("booking_conflicts_total{reason=\"no_availability\"}");

        post(booking(SeedData.FULL_INSPECTION, MONDAY_9AM, vin(1)));
        post(booking(SeedData.FULL_INSPECTION, MONDAY_9AM, vin(2))); // only one qualified technician: 409

        String scrape = scrape();
        assertThat(metricValue(scrape, "appointments_booked_total")).isEqualTo(bookedBefore + 1);
        assertThat(metricValue(scrape, "booking_conflicts_total{reason=\"no_availability\"}"))
                .isEqualTo(noAvailabilityBefore + 1);
        assertThat(scrape)
                .contains("booking_conflicts_total{reason=\"resource\"}")
                .contains("booking_conflicts_total{reason=\"vehicle\"}")
                .contains("booking_assignment_retries_total")
                .contains("booking_duration_seconds_count")
                .contains("http_server_requests_seconds_count");
    }

    @Test
    void bookingLogsContainNoPersonalData(CapturedOutput output) {
        Map<String, Object> body = booking(SeedData.OIL_CHANGE, MONDAY_9AM, vin(7));
        @SuppressWarnings("unchecked")
        Map<String, Object> customer = (Map<String, Object>) body.get("customer");
        customer.put("email", "secret.person@example.com");
        customer.put("phone", "+447911123456");
        body.put("notes", "Private note about the customer");

        post(body);
        post(body); // overlapping booking for the same vehicle is rejected and logged too

        assertThat(output.getAll())
                .contains("Booking CREATED")
                .contains("Booking rejected code=VEHICLE_ALREADY_BOOKED")
                .contains("vinSuffix=" + vin(7).substring(11))
                .doesNotContain("secret.person@example.com")
                .doesNotContain("+447911123456")
                .doesNotContain("Private note")
                .doesNotContain(vin(7));
    }

    private void post(Map<String, Object> body) {
        given().port(port).contentType(ContentType.JSON).body(body).post("/api/v1/appointments");
    }

    private String scrape() {
        return given().port(port)
                .get("/actuator/prometheus")
                .then()
                .statusCode(200)
                .extract()
                .asString();
    }

    private double metricValue(String series) {
        return metricValue(scrape(), series);
    }

    private static double metricValue(String scrape, String series) {
        Matcher matcher = Pattern.compile("(?m)^" + Pattern.quote(series) + " ([0-9.Ee+-]+)$")
                .matcher(scrape);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0.0;
    }
}
