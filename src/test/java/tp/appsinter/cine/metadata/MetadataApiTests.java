package tp.appsinter.cine.metadata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tp.appsinter.cine.PostgresTestcontainersConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los metadatos son públicos y cacheables. Corre contra un servidor real y no contra MockMvc: que
 * {@code HEAD} no lleve cuerpo lo decide el contenedor de servlets, que MockMvc no simula.
 *
 * <p>{@code HEAD} tiene que anunciar el mismo {@code Cache-Control} y el mismo {@code ETag} que
 * {@code GET}, o una caché intermedia que valida con {@code HEAD} los descartaría.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(PostgresTestcontainersConfiguration.class)
@DisplayName("Metadata")
class MetadataApiTests {

    @Autowired
    private RestTestClient client;

    @Test
    @DisplayName("serves enum labels and business config")
    void servesMetadata() {
        client.get().uri("/api/metadata/enums").exchange()
                .expectStatus().isOk()
                .expectHeader().exists(HttpHeaders.ETAG)
                .expectBody()
                .jsonPath("$.screenRoomType[?(@.value=='THREED')].label").isEqualTo(List.of("3D"))
                .jsonPath("$.screenRoomType[?(@.value=='FOURD')].label").isEqualTo(List.of("4D"))
                .jsonPath("$.movieGenre[?(@.value=='SCI_FI')].label").isEqualTo(List.of("Sci-Fi"));

        client.get().uri("/api/metadata/config").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.premiumSeatMultiplier").isEqualTo(1.5)
                .jsonPath("$.screeningTurnoverMinutes").isEqualTo(15)
                .jsonPath("$.currency").isEqualTo("USD")
                .jsonPath("$.seatHoldTtlSeconds").isNumber();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/metadata/config", "/api/metadata/enums"})
    @DisplayName("HEAD carries the same caching headers as GET and no body")
    void headMatchesGet(String path) {
        EntityExchangeResult<byte[]> viaGet = client.get().uri(path).exchange()
                .expectStatus().isOk()
                .expectBody().returnResult();
        HttpHeaders headers = viaGet.getResponseHeaders();
        assertThat(headers.getETag()).isNotBlank();
        assertThat(headers.getCacheControl()).contains("max-age=86400").contains("public");

        client.head().uri(path).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, headers.getCacheControl())
                .expectHeader().valueEquals(HttpHeaders.ETAG, headers.getETag())
                .expectHeader().doesNotExist(HttpHeaders.PRAGMA)
                .expectBody().isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/metadata/config", "/api/metadata/enums"})
    @DisplayName("answers 304 when the client already has the current version")
    void notModified(String path) {
        String etag = client.get().uri(path).exchange()
                .expectStatus().isOk()
                .expectBody().returnResult()
                .getResponseHeaders().getETag();

        client.get().uri(path).header(HttpHeaders.IF_NONE_MATCH, etag).exchange()
                .expectStatus().isNotModified()
                .expectBody().isEmpty();
    }
}
