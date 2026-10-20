package tp.appsinter.cine.screening;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tp.appsinter.cine.hold.dto.request.SeatHoldRequestDTO;
import tp.appsinter.cine.reservation.dto.request.ReservationRequestDTO;
import tp.appsinter.cine.room.Seat;
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
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL;
import static tp.appsinter.cine.support.TestUsers.ADMIN;
import static tp.appsinter.cine.support.TestUsers.CUSTOMER;

@ApiIntegrationTest
@DisplayName("Screenings")
class ScreeningApiTests {

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private ApiClient api;
    @Autowired
    private CatalogueFixture fixture;
    @Autowired
    private UserService userService;
    @Autowired
    private MovieScreeningRepository movieScreeningRepository;

    private Catalogue catalogue;
    private MovieScreening screening;

    @BeforeEach
    void setUp() {
        TestUsers.createAll(userService);
        catalogue = fixture.create(SeatType.NORMAL, SeatType.NORMAL, SeatType.NORMAL);
        screening = catalogue.screening();
    }

    private MockMvcTester.MockMvcRequestBuilder archive(UUID screeningId, boolean archived) {
        return mvc.patch().uri("/api/screenings/{id}", screeningId)
                .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(new ArchiveRequestDTO(archived)));
    }

    private MockMvcTester.MockMvcRequestBuilder schedule(OffsetDateTime startsAt) {
        return mvc.post().uri("/api/screenings")
                .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(new ScreeningRequestDTO(catalogue.movie().getUuid(), catalogue.room().getUuid(),
                        startsAt, new BigDecimal("12.00"))));
    }

    private MockMvcTester.MockMvcRequestBuilder buy(String path, Seat seat) {
        return mvc.post().uri(path)
                .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(new ReservationRequestDTO(screening.getUuid(), List.of(seat.getUuid()), List.of())));
    }

    private String book(Seat seat) {
        MvcTestResult booked = buy("/api/reservations", seat).exchange();
        assertThat(booked).hasStatus(HttpStatus.CREATED);
        return ApiClient.read(booked, "$.uuid");
    }

    @Nested
    @DisplayName("public listing")
    class PublicListing {

        @Test
        @DisplayName("lists screenings with and without date bounds")
        void listsScreenings() {
            // Regresión: sin `from`/`to`, PostgreSQL no podía tipar el parámetro nulo y devolvía 500.
            assertThat(mvc.get().uri("/api/screenings"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
            assertThat(mvc.get().uri("/api/screenings").param("upcomingOnly", "true"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.content[0].uuid").isEqualTo(screening.getUuid().toString());
            assertThat(mvc.get().uri("/api/screenings")
                            .param("from", fixture.now().plusDays(3).toString())
                            .param("to", fixture.now().plusDays(10).toString()))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(0);
            assertThat(mvc.get().uri("/api/screenings").param("movieId", catalogue.movie().getUuid().toString()))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
        }

        @Test
        @DisplayName("reports availability counts")
        void reportsAvailability() {
            book(catalogue.seat(1));

            assertThat(mvc.get().uri("/api/screenings/{id}", screening.getUuid()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.totalSeats", total -> assertThat(total).isEqualTo(3))
                    .hasPathSatisfying("$.availableSeats", available -> assertThat(available).isEqualTo(2))
                    .hasPathSatisfying("$.heldSeats", held -> assertThat(held).isEqualTo(0))
                    .hasPathSatisfying("$.soldOut", soldOut -> assertThat(soldOut).isEqualTo(false));
        }

        @Test
        @DisplayName("groups upcoming screenings by movie in chronological order")
        void nowShowing() {
            MovieScreening earlier = fixture.screening(catalogue.movie(), catalogue.room(), fixture.now().plusDays(1));
            fixture.movie("Unscheduled", 90);

            assertThat(mvc.get().uri("/api/movies/now-showing"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.totalElements", total -> assertThat(total).isEqualTo(1))
                    .hasPathSatisfying("$.content[0].movie.movieName", name -> assertThat(name).isEqualTo("Arrival"))
                    .hasPathSatisfying("$.content[0].screenings[*].uuid", ids -> assertThat(ids).asArray()
                            .containsExactly(earlier.getUuid().toString(), screening.getUuid().toString()))
                    .hasPathSatisfying("$.content[0].screenings[0].availableSeats",
                            seats -> assertThat(seats).isEqualTo(3));

            assertThat(mvc.get().uri("/api/movies/now-showing")
                            .param("to", fixture.now().plusDays(1).plusHours(1).toString()))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.content[0].screenings").asArray().hasSize(1);
        }
    }

    @Nested
    @DisplayName("seat map")
    class IncludeSeatMap {

        @Test
        @DisplayName("embeds the seat map only when asked to")
        void embedsSeatMap() {
            assertThat(mvc.get().uri("/api/screenings/{id}", screening.getUuid()).param("include", "seatMap"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.seatMap.screeningId",
                            id -> assertThat(id).isEqualTo(screening.getUuid().toString()))
                    .hasPathSatisfying("$.seatMap.seats", seats -> assertThat(seats).asArray().hasSize(3));
            assertThat(mvc.get().uri("/api/screenings/{id}", screening.getUuid()))
                    .hasStatusOk()
                    .bodyJson().doesNotHavePath("$.seatMap");
        }

        @Test
        @DisplayName("rejects an unknown include value")
        void rejectsUnknown() {
            assertThat(mvc.get().uri("/api/screenings/{id}", screening.getUuid()).param("include", "everything"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("VALIDATION_FAILED"))
                    .hasPathSatisfying("$.violations[0].field", field -> assertThat(field).isEqualTo("include"))
                    .hasPathSatisfying("$.violations[0].code", code -> assertThat(code).isEqualTo("OneOf"))
                    .hasPathSatisfying("$.violations[0].params.values[0]",
                            value -> assertThat(value).isEqualTo("seatMap"));
        }
    }

    @Nested
    @DisplayName("editing")
    class Editing {

        @Test
        @DisplayName("keeps the end time when only the price changes")
        void priceOnlyEditKeepsEnd() {
            // Un fin que la regla actual no produciría —por ejemplo, de antes de cambiar el recambio—
            // tiene que sobrevivir a una edición que no toca el horario.
            screening.setEndsAt(screening.getEndsAt().plusMinutes(10));
            OffsetDateTime endsAt = movieScreeningRepository.save(screening).getEndsAt();

            MvcTestResult edited = mvc.put().uri("/api/screenings/{id}", screening.getUuid())
                    .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(api.json(new ScreeningRequestDTO(catalogue.movie().getUuid(), catalogue.room().getUuid(),
                            screening.getStartsAt(), new BigDecimal("12.50"))))
                    .exchange();

            assertThat(edited)
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.basePrice").convertTo(BIG_DECIMAL).isEqualByComparingTo("12.50");
            assertThat(OffsetDateTime.parse(ApiClient.read(edited, "$.endsAt")).truncatedTo(ChronoUnit.MILLIS))
                    .isAtSameInstantAs(endsAt.truncatedTo(ChronoUnit.MILLIS));
        }
    }

    @Nested
    @DisplayName("archive instead of delete")
    class Archive {

        @Test
        @DisplayName("archives a screening whose tickets were all cancelled, keeping its reservations")
        void archivesCancelledScreening() {
            String reservation = book(catalogue.seat(1));
            assertThat(mvc.post().uri("/api/reservations/{id}/cancellation", reservation)
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER)))
                    .hasStatusOk();

            assertThat(archive(screening.getUuid(), true))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.archived").isEqualTo(true);

            assertThat(mvc.get().uri("/api/screenings"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.content[*].uuid").asArray()
                    .doesNotContain(screening.getUuid().toString());
            assertThat(mvc.get().uri("/api/screenings").param("includeArchived", "true"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.content[*].uuid").asArray()
                    .contains(screening.getUuid().toString());
            assertThat(mvc.get().uri("/api/screenings/{id}", screening.getUuid()))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.archived").isEqualTo(true);
            assertThat(mvc.get().uri("/api/reservations/{id}", reservation)
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER)))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.movieScreeningId").isEqualTo(screening.getUuid().toString());
        }

        @Test
        @DisplayName("refuses to archive an upcoming screening with active tickets")
        void refusesSoldScreening() {
            book(catalogue.seat(1));

            assertThat(archive(screening.getUuid(), true))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_IN_USE");
        }

        @Test
        @DisplayName("rejects holds, quotes and bookings on an archived screening")
        void archivedScreeningIsClosed() {
            assertThat(archive(screening.getUuid(), true)).hasStatusOk();

            assertThat(mvc.post().uri("/api/screenings/{id}/holds", screening.getUuid())
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new SeatHoldRequestDTO(List.of(catalogue.seat(1).getUuid())))))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("RESOURCE_ARCHIVED"))
                    .hasPathSatisfying("$.context.resource",
                            resource -> assertThat(resource).isEqualTo("Movie screening"));
            assertThat(buy("/api/reservations:quote", catalogue.seat(1)))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_ARCHIVED");
            assertThat(buy("/api/reservations", catalogue.seat(1)))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_ARCHIVED");
        }

        @Test
        @DisplayName("frees the slot of an archived screening and checks it again on restore")
        void archivingFreesTheSlot() {
            assertThat(archive(screening.getUuid(), true)).hasStatusOk();
            assertThat(schedule(screening.getStartsAt())).hasStatus(HttpStatus.CREATED);

            assertThat(archive(screening.getUuid(), false))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("SCREENING_OVERLAP");
        }
    }
}
