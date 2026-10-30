package com.prelude.klasm.common.error;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;

import com.prelude.klasm.TestcontainersConfiguration;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@Import({TestcontainersConfiguration.class, ErrorHandlingIT.ErrorTestController.class})
class ErrorHandlingIT {

    private static final String TRACE_ID = "[0-9a-f]{32}";

    @LocalServerPort
    int port;

    @Test
    void malformedJsonReturnsValidationError() {
        Response response = given().port(port)
                .contentType(ContentType.JSON)
                .body("{\"name\": ")
                .post("/test/errors/body");

        assertProblem(response, 400, "VALIDATION_ERROR");
        response.then().body("detail", equalTo("Malformed request body."));
    }

    @Test
    void invalidBodyFieldsReturnValidationErrorWithFieldList() {
        Response response = given().port(port)
                .contentType(ContentType.JSON)
                .body("{\"name\": \" \", \"code\": \"TOO-LONG\"}")
                .post("/test/errors/body");

        assertProblem(response, 400, "VALIDATION_ERROR");
        response.then().body("errors.field", containsInAnyOrder("name", "code"));
    }

    @Test
    void invalidQueryParameterReturnsValidationError() {
        Response response = given().port(port).queryParam("size", 500).get("/test/errors/page");

        assertProblem(response, 400, "VALIDATION_ERROR");
        response.then().body("errors.field", containsInAnyOrder("size"));
    }

    @Test
    void missingRequiredParameterReturnsValidationError() {
        Response response = given().port(port).get("/test/errors/page");

        assertProblem(response, 400, "VALIDATION_ERROR");
    }

    @Test
    void unsupportedMethodKeepsTraceIdWithoutCatalogCode() {
        given().port(port)
                .delete("/test/errors/ok")
                .then()
                .statusCode(405)
                .contentType("application/problem+json")
                .body("traceId", matchesPattern(TRACE_ID));
    }

    @Test
    void malformedUuidReturnsValidationError() {
        Response response = given().port(port).get("/test/errors/items/not-a-uuid");

        assertProblem(response, 400, "VALIDATION_ERROR");
        response.then().body("errors.field", containsInAnyOrder("id"));
    }

    @Test
    void missingResourceReturnsNotFound() {
        UUID id = UUID.randomUUID();

        Response response = given().port(port).get("/test/errors/items/" + id);

        assertProblem(response, 404, "NOT_FOUND");
        response.then().body("detail", equalTo("Item " + id + " not found."));
    }

    @Test
    void unknownRouteReturnsNotFound() {
        Response response = given().port(port).get("/api/v1/does-not-exist");

        assertProblem(response, 404, "NOT_FOUND");
    }

    @Test
    void unexpectedErrorReturnsInternalErrorWithoutInternalDetails() {
        Response response = given().port(port).get("/test/errors/boom");

        assertProblem(response, 500, "INTERNAL_ERROR");
        assertThat(response.asString())
                .doesNotContain("IllegalStateException")
                .doesNotContain("java.")
                .doesNotContain("SELECT")
                .doesNotContain("\"trace\"");
    }

    @Test
    void successfulResponseCarriesTraceIdHeader() {
        given().port(port).get("/test/errors/ok").then().statusCode(200).header("X-Trace-Id", matchesPattern(TRACE_ID));
    }

    private static void assertProblem(Response response, int status, String code) {
        response.then()
                .statusCode(status)
                .contentType("application/problem+json")
                .header("X-Trace-Id", matchesPattern(TRACE_ID))
                .body("status", equalTo(status))
                .body("code", equalTo(code))
                .body("traceId", equalTo(response.header("X-Trace-Id")));
    }

    record SampleBody(@NotBlank String name, @Size(max = 3) String code) {}

    @RestController
    @RequestMapping("/test/errors")
    static class ErrorTestController {

        @PostMapping("/body")
        String body(@Valid @RequestBody SampleBody body) {
            return "ok";
        }

        @GetMapping("/page")
        String page(@RequestParam @Max(100) int size) {
            return "ok";
        }

        @GetMapping("/items/{id}")
        String item(@PathVariable UUID id) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Item " + id + " not found.");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("SELECT * FROM secret_table failed");
        }

        @GetMapping("/ok")
        String ok() {
            return "ok";
        }
    }
}
