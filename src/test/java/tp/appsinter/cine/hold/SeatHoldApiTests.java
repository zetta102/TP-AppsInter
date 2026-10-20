package tp.appsinter.cine.hold;

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
import tp.appsinter.cine.support.ApiClient;
import tp.appsinter.cine.support.ApiIntegrationTest;
import tp.appsinter.cine.support.CatalogueFixture;
import tp.appsinter.cine.support.CatalogueFixture.Catalogue;
import tp.appsinter.cine.support.MutableClock;
import tp.appsinter.cine.support.TestUsers;
import tp.appsinter.cine.user.UserService;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static tp.appsinter.cine.support.TestUsers.CUSTOMER;
import static tp.appsinter.cine.support.TestUsers.OTHER_CUSTOMER;

@ApiIntegrationTest
@DisplayName("Seat holds")
class SeatHoldApiTests {

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private ApiClient api;
    @Autowired
    private CatalogueFixture fixture;
    @Autowired
    private UserService userService;
    @Autowired
    private MutableClock clock;

    private Catalogue catalogue;

    @BeforeEach
    void setUp() {
        TestUsers.createAll(userService);
        catalogue = fixture.create(SeatType.NORMAL, SeatType.NORMAL, SeatType.NORMAL);
    }

    private static List<UUID> ids(Seat... seats) {
        return Arrays.stream(seats).map(Seat::getUuid).toList();
    }

    private MockMvcTester.MockMvcRequestBuilder hold(String email, Seat... seats) {
        return mvc.post().uri("/api/screenings/{id}/holds", catalogue.screening().getUuid())
                .header(HttpHeaders.AUTHORIZATION, api.bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(new SeatHoldRequestDTO(ids(seats))));
    }

    private String holdId(String email, Seat... seats) {
        MvcTestResult created = hold(email, seats).exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return ApiClient.read(created, "$.uuid");
    }

    private MockMvcTester.MockMvcRequestBuilder replace(String email, String holdId, Seat... seats) {
        return mvc.put().uri("/api/holds/{id}", holdId)
                .header(HttpHeaders.AUTHORIZATION, api.bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(new SeatHoldRequestDTO(ids(seats))));
    }

    private MockMvcTester.MockMvcRequestBuilder buy(String email, UUID holdId, Seat... seats) {
        return mvc.post().uri("/api/reservations")
                .header(HttpHeaders.AUTHORIZATION, api.bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(new ReservationRequestDTO(catalogue.screening().getUuid(), ids(seats), List.of(),
                        holdId)));
    }

    /**
     * Cómo ve una butaca otro cliente en el mapa: {@code [available, held]}.
     */
    private List<Object> seatAsSeenBy(String email, Seat seat) {
        MvcTestResult map = mvc.get().uri("/api/screenings/{id}/seats", catalogue.screening().getUuid())
                .header(HttpHeaders.AUTHORIZATION, api.bearer(email))
                .exchange();
        assertThat(map).hasStatusOk();
        String path = "$.seats[?(@.seatId=='%s')]".formatted(seat.getUuid());
        return List.of(
                assertThat(map).bodyJson().extractingPath(path + ".available").asArray().actual()[0],
                assertThat(map).bodyJson().extractingPath(path + ".held").asArray().actual()[0]);
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("holds a seat, blocks other buyers and releases it idempotently")
        void holdLifecycle() {
            MvcTestResult created = hold(CUSTOMER, catalogue.seat(1)).exchange();
            assertThat(created)
                    .hasStatus(HttpStatus.CREATED)
                    .containsHeader(HttpHeaders.LOCATION)
                    .bodyJson()
                    .hasPath("$.expiresAt")
                    .hasPathSatisfying("$.ttlSeconds", ttl -> assertThat(ttl).asNumber());
            String holdId = ApiClient.read(created, "$.uuid");

            assertThat(hold(OTHER_CUSTOMER, catalogue.seat(1)))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("SEATS_UNAVAILABLE");
            assertThat(seatAsSeenBy(OTHER_CUSTOMER, catalogue.seat(1))).containsExactly(false, true);
            assertThat(seatAsSeenBy(CUSTOMER, catalogue.seat(1))).containsExactly(true, false);

            assertThat(mvc.delete().uri("/api/holds/{id}", holdId).header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER)))
                    .hasStatus(HttpStatus.NO_CONTENT);
            assertThat(mvc.delete().uri("/api/holds/{id}", holdId).header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER)))
                    .as("releasing twice is not an error")
                    .hasStatus(HttpStatus.NO_CONTENT);

            assertThat(hold(OTHER_CUSTOMER, catalogue.seat(1))).hasStatus(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("stops blocking other buyers once it expires, even before the sweep")
        void expires() {
            String holdId = holdId(CUSTOMER, catalogue.seat(1));
            clock.advance(Duration.ofMinutes(6));

            assertThat(seatAsSeenBy(OTHER_CUSTOMER, catalogue.seat(1))).containsExactly(true, false);
            assertThat(mvc.get().uri("/api/holds/{id}", holdId).header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER)))
                    .hasStatus(HttpStatus.NOT_FOUND);
            assertThat(hold(OTHER_CUSTOMER, catalogue.seat(1))).hasStatus(HttpStatus.CREATED);
        }
    }

    @Nested
    @DisplayName("atomic replacement")
    class Replacement {

        @Test
        @DisplayName("swaps the seat set in place and keeps the expiry")
        void replacesKeepingExpiry() {
            MvcTestResult created = hold(CUSTOMER, catalogue.seat(1)).exchange();
            assertThat(created).hasStatus(HttpStatus.CREATED);
            String holdId = ApiClient.read(created, "$.uuid");

            MvcTestResult replaced = replace(CUSTOMER, holdId, catalogue.seat(1), catalogue.seat(2)).exchange();
            assertThat(replaced)
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.uuid", uuid -> assertThat(uuid).isEqualTo(holdId))
                    .hasPathSatisfying("$.seatIds", ids -> assertThat(ids).asArray().hasSize(2));
            // El alta responde con la hora en memoria (nanosegundos) y el reemplazo con la guardada
            // (microsegundos): se comparan al milisegundo.
            assertThat(OffsetDateTime.parse(ApiClient.read(replaced, "$.expiresAt")).truncatedTo(ChronoUnit.MILLIS))
                    .isAtSameInstantAs(OffsetDateTime.parse(ApiClient.read(created, "$.expiresAt"))
                            .truncatedTo(ChronoUnit.MILLIS));

            assertThat(hold(OTHER_CUSTOMER, catalogue.seat(2)))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("SEATS_UNAVAILABLE");

            assertThat(replace(CUSTOMER, holdId, catalogue.seat(3))).hasStatusOk();
            assertThat(hold(OTHER_CUSTOMER, catalogue.seat(1), catalogue.seat(2))).hasStatus(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("fails like a new hold on sold seats, and 404s on someone else's hold")
        void sameErrorsAsCreate() {
            String holdId = holdId(CUSTOMER, catalogue.seat(1));
            assertThat(buy(OTHER_CUSTOMER, null, catalogue.seat(2))).hasStatus(HttpStatus.CREATED);

            assertThat(replace(CUSTOMER, holdId, catalogue.seat(1), catalogue.seat(2)))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("SEATS_UNAVAILABLE");
            assertThat(replace(OTHER_CUSTOMER, holdId, catalogue.seat(3)))
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("purchase")
    class Purchase {

        @Test
        @DisplayName("frees the held seats the purchase did not take")
        void freesUnboughtSeats() {
            UUID holdId = UUID.fromString(holdId(CUSTOMER, catalogue.seat(1), catalogue.seat(2)));

            assertThat(buy(CUSTOMER, holdId, catalogue.seat(1))).hasStatus(HttpStatus.CREATED);

            assertThat(seatAsSeenBy(OTHER_CUSTOMER, catalogue.seat(2))).containsExactly(true, false);
            assertThat(hold(OTHER_CUSTOMER, catalogue.seat(2))).hasStatus(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("releases the buyer's own hold even when the purchase does not name it")
        void releasesUnnamedOwnHold() {
            String holdId = holdId(CUSTOMER, catalogue.seat(1));

            assertThat(buy(CUSTOMER, null, catalogue.seat(1))).hasStatus(HttpStatus.CREATED);

            assertThat(mvc.get().uri("/api/holds/{id}", holdId).header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER)))
                    .hasStatus(HttpStatus.NOT_FOUND);
            assertThat(mvc.get().uri("/api/screenings/{id}", catalogue.screening().getUuid()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.heldSeats", held -> assertThat(held).isEqualTo(0))
                    .hasPathSatisfying("$.availableSeats", available -> assertThat(available).isEqualTo(2));
        }

        @Test
        @DisplayName("rejects a hold that does not cover every seat being bought")
        void rejectsPartialHold() {
            UUID holdId = UUID.fromString(holdId(CUSTOMER, catalogue.seat(1)));

            assertThat(buy(CUSTOMER, holdId, catalogue.seat(1), catalogue.seat(2)))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("SEATS_UNAVAILABLE");
        }
    }
}
