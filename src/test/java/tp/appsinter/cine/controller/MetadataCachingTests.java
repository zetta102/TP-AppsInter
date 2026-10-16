package tp.appsinter.cine.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tp.appsinter.cine.PostgresTestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/api/metadata/config", "/api/metadata/enums"})
    @DisplayName("HEAD carries the same caching headers as GET")
    void headMatchesGet(String path) throws Exception {
        MvcResult viaGet = mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        MvcResult viaHead = mockMvc.perform(head(path)).andExpect(status().isOk()).andReturn();

        String cacheControl = viaGet.getResponse().getHeader(HttpHeaders.CACHE_CONTROL);
        assertThat(cacheControl).contains("max-age=86400").contains("public");
        assertThat(viaHead.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo(cacheControl);
        assertThat(viaHead.getResponse().getHeader(HttpHeaders.ETAG))
                .isNotBlank()
                .isEqualTo(viaGet.getResponse().getHeader(HttpHeaders.ETAG));
        assertThat(viaHead.getResponse().getHeader(HttpHeaders.PRAGMA)).isNull();
        // El cuerpo no se comprueba: lo descarta el contenedor de servlets, que MockMvc no simula.
    }
}
