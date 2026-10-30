package com.prelude.klasm;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared setup for API integration tests: one Spring context and one PostgreSQL container for all subclasses, a
 * movable clock fixed at {@link MutableClock#DEFAULT_NOW}, and an empty appointment table before every test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
public abstract class ApiTestSupport {

    /** Monday 2030-01-07 09:00 in London (UTC+0 in winter), six days after the test clock. */
    protected static final String MONDAY_9AM = "2030-01-07T09:00:00Z";

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void resetState() {
        clock.reset();
        jdbc.execute("TRUNCATE appointment, customer, vehicle");
    }

    /** Valid booking body for London; the VIN also makes the customer email unique. */
    protected static Map<String, Object> booking(UUID serviceTypeId, String startTime, String vin) {
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("fullName", "Customer " + vin);
        customer.put("email", vin.toLowerCase() + "@example.com");
        Map<String, Object> vehicle = new LinkedHashMap<>();
        vehicle.put("vin", vin);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dealershipId", SeedData.LONDON.toString());
        body.put("serviceTypeId", serviceTypeId.toString());
        body.put("startTime", startTime);
        body.put("customer", customer);
        body.put("vehicle", vehicle);
        return body;
    }

    /** A valid VIN that differs by its last two digits. */
    protected static String vin(int n) {
        return "1HGCM82633A0043" + String.format("%02d", n);
    }
}
