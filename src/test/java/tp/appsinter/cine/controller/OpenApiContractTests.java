package tp.appsinter.cine.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tp.appsinter.cine.PostgresTestcontainersConfiguration;
import tp.appsinter.cine.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private MockMvc mockMvc;

    private String docs;

    @BeforeAll
    void fetchDocs() throws Exception {
        docs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Files.writeString(Path.of("target", "api-docs.json"), docs);
    }

    private <T> T read(String path) {
        return JsonPath.read(docs, path);
    }

    @Test
    @DisplayName("marks non-nullable record components as required and nullable ones as type|null")
    void requiredAndNullable() {
        List<String> required = read("$.components.schemas.Screening.required");
        assertThat(required).contains("uuid", "movieName", "startsAt", "heldSeats", "availableSeats");
        List<String> heldSeatsType = read("$.components.schemas.Screening.properties.heldSeats.type");
        assertThat(heldSeatsType).containsExactlyInAnyOrder("integer", "null");

        List<String> movieRequired = read("$.components.schemas.Movie.required");
        assertThat(movieRequired).contains("uuid", "movieName", "genres", "posterUrl");
        List<String> posterType = read("$.components.schemas.Movie.properties.posterUrl.type");
        assertThat(posterType).containsExactlyInAnyOrder("string", "null");
    }

    @Test
    @DisplayName("publishes the problem envelope with the error code registry as an enum")
    void problemSchema() {
        assertThat((String) read("$.components.schemas.Problem.properties.code.$ref"))
                .isEqualTo("#/components/schemas/ErrorCode");
        List<String> codes = read("$.components.schemas.ErrorCode.enum");
        assertThat(codes).containsExactlyElementsOf(Arrays.stream(ErrorCode.values()).map(Enum::name).toList());
        assertThat((String) read("$.components.schemas.Problem.properties.violations.items.$ref"))
                .isEqualTo("#/components/schemas/Violation");
        Map<String, Object> defaultResponse = read("$.paths['/api/movies'].get.responses.default");
        assertThat(defaultResponse).containsKey("content");
    }

    @Test
    @DisplayName("documents each list's sort whitelist as x-sort-properties")
    void sortProperties() {
        List<List<String>> movies = read("$.paths['/api/movies'].get.parameters[?(@.name == 'sort')]['x-sort-properties']");
        assertThat(movies).singleElement().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .contains("movieName", "releaseYear");
        List<List<String>> products = read("$.paths['/api/products'].get.parameters[?(@.name == 'sort')]['x-sort-properties']");
        assertThat(products).singleElement().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .contains("price");
    }

    @Test
    @DisplayName("keeps a field omitted when null optional instead of nullable")
    void omittedFieldsAreOptional() {
        List<String> required = read("$.components.schemas.Screening.required");
        assertThat(required).contains("archived").doesNotContain("seatMap");
        assertThat((String) read("$.components.schemas.Screening.properties.seatMap.$ref"))
                .isEqualTo("#/components/schemas/SeatMap");
    }

    @Test
    @DisplayName("documents the seat stream payload and the request bodies of the new endpoints")
    void newEndpoints() {
        assertThat((String) read("$.paths['/api/screenings/{uuid}/seats/stream'].get.responses['200']"
                + ".content['text/event-stream'].schema.items.$ref"))
                .isEqualTo("#/components/schemas/SeatState");
        assertThat(docs).doesNotContain("\"SseEmitter\"");
        List<String> archiveRequired = read("$.components.schemas.ArchiveRequest.required");
        assertThat(archiveRequired).containsExactly("archived");
        List<String> includeValues = read("$.paths['/api/screenings/{uuid}'].get.parameters[?(@.name == 'include')].schema.items.enum[*]");
        assertThat(includeValues).containsExactly("seatMap");
    }

    @Test
    @DisplayName("publishes enums as named schemas")
    void enumsAsRefs() {
        List<String> genres = read("$.components.schemas.MovieGenre.enum");
        assertThat(genres).contains("ACTION", "DRAMA");
    }
}
