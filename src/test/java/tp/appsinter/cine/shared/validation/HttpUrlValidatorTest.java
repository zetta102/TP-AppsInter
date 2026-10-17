package tp.appsinter.cine.shared.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HTTP URL validation")
class HttpUrlValidatorTest {

    @ParameterizedTest(name = "accepts {0}")
    @ValueSource(strings = {
            "https://image.tmdb.org/t/p/w500/x.jpg",
            "http://localhost:8080/poster.png",
            "https://cdn.example.com/a%20b.jpg?size=large#top",
            "HTTPS://EXAMPLE.COM/x"
    })
    void acceptsLoadableUrls(String url) {
        assertThat(HttpUrlValidator.isValid(url)).isTrue();
    }

    @ParameterizedTest(name = "rejects {0}")
    @ValueSource(strings = {
            "https://KXEb3yzz0D5lXYdk;[#`E(G@",
            "https://",
            "https:///path-without-host",
            "ftp://example.com/file.jpg",
            "javascript:alert(1)",
            "//example.com/x.jpg",
            "image.jpg",
            "https://exa mple.com/x.jpg",
            "https://example.com/x.jpg\n",
            ""
    })
    void rejectsGarbage(String url) {
        assertThat(HttpUrlValidator.isValid(url)).isFalse();
    }

    @Test
    @DisplayName("treats null as valid so optional fields stay optional")
    void nullIsValid() {
        assertThat(HttpUrlValidator.isValid(null)).isTrue();
    }
}
