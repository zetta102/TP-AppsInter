package tp.appsinter.cine.controller;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonContent;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tp.appsinter.cine.PostgresTestcontainersConfiguration;
import tp.appsinter.cine.exception.ErrorCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

/**
 * El documento OpenAPI es la fuente de los tipos que genera el frontend
 * ({@code npx openapi-typescript /v3/api-docs}), así que su forma es parte del contrato.
 *
 * <p>Deja una copia en {@code target/api-docs.json} para poder inspeccionarla o generar los tipos
 * sin levantar la aplicación.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("OpenAPI contract")
class OpenApiContractTests {

    @Autowired
    private MockMvcTester mvc;

    private JsonContent docs;

    @BeforeAll
    void fetchDocs() throws IOException {
        MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(result).hasStatusOk();
        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        Files.writeString(Path.of("target", "api-docs.json"), json);
        docs = new JsonContent(json);
    }

    @Test
    @DisplayName("marks non-nullable record components as required and nullable ones as type|null")
    void requiredAndNullable() {
        assertThat(docs).extractingPath("$.components.schemas.Screening.required").asArray()
                .contains("uuid", "movieName", "startsAt", "heldSeats", "availableSeats");
        assertThat(docs).extractingPath("$.components.schemas.Screening.properties.heldSeats.type").asArray()
                .containsExactlyInAnyOrder("integer", "null");

        assertThat(docs).extractingPath("$.components.schemas.Movie.required").asArray()
                .contains("uuid", "movieName", "genres", "posterUrl");
        assertThat(docs).extractingPath("$.components.schemas.Movie.properties.posterUrl.type").asArray()
                .containsExactlyInAnyOrder("string", "null");
    }

    @Test
    @DisplayName("publishes the problem envelope with the error code registry as an enum")
    void problemSchema() {
        assertThat(docs).extractingPath("$.components.schemas.Problem.properties.code.$ref")
                .isEqualTo("#/components/schemas/ErrorCode");
        assertThat(docs).extractingPath("$.components.schemas.ErrorCode.enum").asArray()
                .containsExactly(Arrays.stream(ErrorCode.values()).map(Enum::name).toArray());
        assertThat(docs).extractingPath("$.components.schemas.Problem.properties.violations.items.$ref")
                .isEqualTo("#/components/schemas/Violation");
        assertThat(docs).extractingPath("$.paths['/api/movies'].get.responses.default").asMap()
                .containsKey("content");
    }

    @Test
    @DisplayName("documents each list's sort whitelist as x-sort-properties")
    void sortProperties() {
        assertThat(docs).extractingPath("$.paths['/api/movies'].get.parameters[?(@.name == 'sort')]['x-sort-properties']")
                .asArray().singleElement().asInstanceOf(LIST)
                .contains("movieName", "releaseYear");
        assertThat(docs).extractingPath("$.paths['/api/products'].get.parameters[?(@.name == 'sort')]['x-sort-properties']")
                .asArray().singleElement().asInstanceOf(LIST)
                .contains("price");
    }

    @Test
    @DisplayName("keeps a field omitted when null optional instead of nullable")
    void omittedFieldsAreOptional() {
        assertThat(docs).extractingPath("$.components.schemas.Screening.required").asArray()
                .contains("archived").doesNotContain("seatMap");
        assertThat(docs).extractingPath("$.components.schemas.Screening.properties.seatMap.$ref")
                .isEqualTo("#/components/schemas/SeatMap");
    }

    @Test
    @DisplayName("documents the seat stream payload and the request bodies of the new endpoints")
    void newEndpoints() {
        assertThat(docs).extractingPath("$.paths['/api/screenings/{uuid}/seats/stream'].get.responses['200']"
                        + ".content['text/event-stream'].schema.items.$ref")
                .isEqualTo("#/components/schemas/SeatState");
        assertThat(docs.getJson()).doesNotContain("\"SseEmitter\"");
        assertThat(docs).extractingPath("$.components.schemas.ArchiveRequest.required").asArray()
                .containsExactly("archived");
        assertThat(docs).extractingPath("$.paths['/api/screenings/{uuid}'].get.parameters[?(@.name == 'include')].schema.items.enum[*]")
                .asArray().containsExactly("seatMap");
    }

    @Test
    @DisplayName("publishes enums as named schemas")
    void enumsAsRefs() {
        assertThat(docs).extractingPath("$.components.schemas.MovieGenre.enum").asArray()
                .contains("ACTION", "DRAMA");
    }
}
