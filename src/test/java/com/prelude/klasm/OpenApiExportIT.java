package com.prelude.klasm;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@Import(TestcontainersConfiguration.class)
class OpenApiExportIT {

    @LocalServerPort
    int port;

    @Test
    void exportsOpenApiYaml() throws IOException {
        String yaml = given().port(port)
                .get("/v3/api-docs.yaml")
                .then()
                .statusCode(200)
                .extract()
                .asString()
                .replace("http://localhost:" + port, "http://localhost:8080");

        assertThat(yaml).contains("openapi: 3.");
        Path targetPath = Paths.get("openapi.yaml");
        Files.writeString(targetPath, yaml);
        assertThat(Files.exists(targetPath)).isTrue();
    }
}
