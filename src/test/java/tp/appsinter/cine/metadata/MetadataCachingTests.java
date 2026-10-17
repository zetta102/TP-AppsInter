package tp.appsinter.cine.metadata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tp.appsinter.cine.PostgresTestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los metadatos son cacheables: {@code HEAD} tiene que anunciar el mismo {@code Cache-Control} y el
 * mismo {@code ETag} que {@code GET}, o una caché intermedia que valida con {@code HEAD} los
 * descartaría (frontend B13).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfiguration.class)
@DisplayName("Metadata caching")
class MetadataCachingTests {

    @Autowired
    private MockMvcTester mvc;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/metadata/config", "/api/metadata/enums"})
    @DisplayName("HEAD carries the same caching headers as GET")
    void headMatchesGet(String path) {
        MvcTestResult viaGet = mvc.get().uri(path).exchange();
        MvcTestResult viaHead = mvc.head().uri(path).exchange();

        assertThat(viaGet).hasStatusOk().containsHeader(HttpHeaders.ETAG);
        String cacheControl = viaGet.getResponse().getHeader(HttpHeaders.CACHE_CONTROL);
        assertThat(cacheControl).contains("max-age=86400").contains("public");
        assertThat(viaHead).hasStatusOk()
                .hasHeader(HttpHeaders.CACHE_CONTROL, cacheControl)
                .hasHeader(HttpHeaders.ETAG, viaGet.getResponse().getHeader(HttpHeaders.ETAG))
                .doesNotContainHeader(HttpHeaders.PRAGMA);
        // El cuerpo no se comprueba: lo descarta el contenedor de servlets, que MockMvc no simula.
    }
}
