package tp.appsinter.cine.movie;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tp.appsinter.cine.movie.dto.request.MovieRequestDTO;
import tp.appsinter.cine.room.SeatType;
import tp.appsinter.cine.screening.dto.request.ScreeningRequestDTO;
import tp.appsinter.cine.shared.dto.request.ArchiveRequestDTO;
import tp.appsinter.cine.support.ApiClient;
import tp.appsinter.cine.support.ApiIntegrationTest;
import tp.appsinter.cine.support.CatalogueFixture;
import tp.appsinter.cine.support.CatalogueFixture.Catalogue;
import tp.appsinter.cine.support.TestUsers;
import tp.appsinter.cine.user.UserService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static tp.appsinter.cine.support.TestUsers.ADMIN;

@ApiIntegrationTest
@DisplayName("Movies")
class MovieApiTests {

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private ApiClient api;
    @Autowired
    private CatalogueFixture fixture;
    @Autowired
    private UserService userService;

    private Catalogue catalogue;

    @BeforeEach
    void setUp() {
        TestUsers.createAll(userService);
        catalogue = fixture.create(SeatType.NORMAL);
    }

    private static MovieRequestDTO movie(String name, int runtime, @Nullable String posterUrl,
                                         @Nullable Integer releaseYear) {
        return new MovieRequestDTO(name, List.of(MovieGenre.SCI_FI), runtime, null, null, null, posterUrl, null,
                releaseYear, null, null, null);
    }

    private MockMvcTester.MockMvcRequestBuilder create(MovieRequestDTO request) {
        return mvc.post().uri("/api/movies")
                .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(request));
    }

    private MockMvcTester.MockMvcRequestBuilder archive(String resource, UUID id, boolean archived) {
        return mvc.patch().uri("/api/{resource}/{id}", resource, id)
                .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(new ArchiveRequestDTO(archived)));
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("rejects a URL that only looks absolute")
        void rejectsGarbageUrl() {
            assertThat(create(movie("Dune", 155, "https://KXEb3yzz0D5lXYdk;[#`E(G@", null)))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("VALIDATION_FAILED"))
                    .hasPath("$.errors.posterUrl")
                    .hasPathSatisfying("$.violations[0].field", field -> assertThat(field).isEqualTo("posterUrl"))
                    .hasPathSatisfying("$.violations[0].code", code -> assertThat(code).isEqualTo("HttpUrl"));
        }

        @Test
        @DisplayName("accepts a real image URL")
        void acceptsRealUrl() {
            assertThat(create(movie("Dune", 155, "https://image.tmdb.org/t/p/w500/x.jpg", null)))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson().extractingPath("$.posterUrl").isEqualTo("https://image.tmdb.org/t/p/w500/x.jpg");
        }

        @Test
        @DisplayName("refuses a release year too far ahead, on the releaseYear field")
        void rejectsDistantReleaseYear() {
            int tooFar = fixture.now().getYear() + MovieRequestDTO.RELEASE_YEAR_LOOKAHEAD + 1;

            assertThat(create(movie("Dune", 155, null, tooFar)))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.violations[0].field", field -> assertThat(field).isEqualTo("releaseYear"))
                    .hasPathSatisfying("$.violations[0].code", code -> assertThat(code).isEqualTo("MaxYearsAhead"));
        }

        @Test
        @DisplayName("rejects a name longer than the column instead of failing in the database")
        void rejectsOversizedName() {
            assertThat(create(movie("x".repeat(256), 155, null, null)))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.errors.movieName").isNotNull();
        }
    }

    @Nested
    @DisplayName("scheduled movies")
    class ScheduledMovies {

        @Test
        @DisplayName("refuses to change the runtime while screenings are upcoming, but allows other edits")
        void runtimeLockedByScreenings() {
            Movie arrival = catalogue.movie();

            assertThat(mvc.put().uri("/api/movies/{id}", arrival.getUuid())
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(movie("Arrival", 120, null, 2016))))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("RESOURCE_IN_USE"))
                    .hasPathSatisfying("$.context.referencedBy",
                            by -> assertThat(by).isEqualTo("Movie screening"));

            assertThat(mvc.put().uri("/api/movies/{id}", arrival.getUuid())
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(movie("Arrival (2016)", arrival.getRuntime(), null, 2016))))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.movieName").isEqualTo("Arrival (2016)");
        }

        @Test
        @DisplayName("archives a movie only when it has no upcoming screenings")
        void archivesMovie() {
            Movie arrival = catalogue.movie();
            assertThat(archive("movies", arrival.getUuid(), true))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_IN_USE");

            assertThat(archive("screenings", catalogue.screening().getUuid(), true)).hasStatusOk();
            assertThat(archive("movies", arrival.getUuid(), true))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.archived").isEqualTo(true);

            assertThat(mvc.get().uri("/api/movies"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(0);
            assertThat(mvc.get().uri("/api/movies").param("includeArchived", "true"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
            assertThat(mvc.post().uri("/api/screenings")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new ScreeningRequestDTO(arrival.getUuid(), catalogue.room().getUuid(),
                                    fixture.now().plusDays(9), new BigDecimal("12.00")))))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_ARCHIVED");
        }
    }
}
