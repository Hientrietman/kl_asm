package com.prelude.klasm;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** FR-6: read-only reference data from the V3 seed. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@Import(TestcontainersConfiguration.class)
class ReferenceDataIT {

    @LocalServerPort
    int port;

    private final JdbcTemplate jdbc;

    @Autowired
    ReferenceDataIT(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Test
    void listsDealershipsWithTimeZoneAndWorkingHours() {
        String london = "find { it.id == '" + SeedData.LONDON + "' }";

        given().port(port)
                .get("/api/v1/dealerships")
                .then()
                .statusCode(200)
                .body("id", hasItems(SeedData.LONDON.toString(), SeedData.HO_CHI_MINH.toString()))
                .body(london + ".timeZone", equalTo("Europe/London"))
                .body(
                        london + ".workingHours.dayOfWeek",
                        contains("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"))
                .body(london + ".workingHours[0].openTime", equalTo("08:00:00"))
                .body(london + ".workingHours[0].closeTime", equalTo("17:00:00"))
                .body(london + ".workingHours[5].closeTime", equalTo("12:00:00"));
    }

    @Test
    void listsServiceTypesOrderedByCode() {
        given().port(port)
                .get("/api/v1/service-types")
                .then()
                .statusCode(200)
                .body("code", contains("BRAKE_SERVICE", "FULL_INSPECTION", "OIL_CHANGE", "TIRE_ROTATION"))
                .body("find { it.code == 'OIL_CHANGE' }.id", equalTo(SeedData.OIL_CHANGE.toString()))
                .body("find { it.code == 'OIL_CHANGE' }.durationMinutes", equalTo(60));
    }

    @Test
    void listsServiceBaysOfDealershipOrderedByCode() {
        given().port(port)
                .get("/api/v1/dealerships/{id}/service-bays", SeedData.LONDON)
                .then()
                .statusCode(200)
                .body("code", contains("BAY-01", "BAY-02", "BAY-03"))
                .body("id[0]", equalTo(SeedData.LONDON_BAY_1.toString()));
    }

    @Test
    void listsTechniciansWithQualifications() {
        String fullInspection = SeedData.FULL_INSPECTION.toString();

        given().port(port)
                .get("/api/v1/dealerships/{id}/technicians", SeedData.LONDON)
                .then()
                .statusCode(200)
                .body("$", hasSize(4))
                .body(
                        "findAll { it.serviceTypeIds.contains('" + fullInspection + "') }.id",
                        contains(SeedData.LONDON_TECH_1.toString()));
    }

    @Test
    void unknownDealershipReturnsNotFound() {
        UUID unknown = UUID.randomUUID();

        for (String path : new String[] {"service-bays", "technicians"}) {
            given().port(port)
                    .get("/api/v1/dealerships/{id}/" + path, unknown)
                    .then()
                    .statusCode(404)
                    .contentType("application/problem+json")
                    .body("code", equalTo("NOT_FOUND"));
        }
    }

    @Test
    void inactiveDealershipIsHiddenAndReturnsNotFound() {
        UUID inactive = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO dealership (id, name, time_zone, active) VALUES (?, 'Closed', 'Europe/Paris', false)",
                inactive);
        try {
            given().port(port)
                    .get("/api/v1/dealerships")
                    .then()
                    .statusCode(200)
                    .body("id", not(hasItems(inactive.toString())));
            given().port(port)
                    .get("/api/v1/dealerships/{id}/service-bays", inactive)
                    .then()
                    .statusCode(404)
                    .body("code", equalTo("NOT_FOUND"));
        } finally {
            jdbc.update("DELETE FROM dealership WHERE id = ?", inactive);
        }
    }

    @Test
    void openApiDocumentListsReferenceEndpoints() {
        given().port(port)
                .get("/v3/api-docs")
                .then()
                .statusCode(200)
                .body("paths", hasKey("/api/v1/dealerships"))
                .body("paths", hasKey("/api/v1/service-types"))
                .body("paths", hasKey("/api/v1/dealerships/{dealershipId}/service-bays"))
                .body("paths", hasKey("/api/v1/dealerships/{dealershipId}/technicians"));
    }
}
