package tp.appsinter.cine.room;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tp.appsinter.cine.hold.dto.request.SeatHoldRequestDTO;
import tp.appsinter.cine.reservation.dto.request.ReservationRequestDTO;
import tp.appsinter.cine.room.dto.request.BulkSeatsRequestDTO;
import tp.appsinter.cine.room.dto.request.BulkSeatsRequestDTO.SeatDefinition;
import tp.appsinter.cine.room.dto.request.GenerateSeatsRequestDTO;
import tp.appsinter.cine.room.dto.request.RoomRequestDTO;
import tp.appsinter.cine.room.dto.request.SeatConflictPolicy;
import tp.appsinter.cine.room.dto.request.SeatRequestDTO;
import tp.appsinter.cine.support.ApiClient;
import tp.appsinter.cine.support.ApiIntegrationTest;
import tp.appsinter.cine.support.CatalogueFixture;
import tp.appsinter.cine.support.CatalogueFixture.Catalogue;
import tp.appsinter.cine.support.TestUsers;
import tp.appsinter.cine.user.UserService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static tp.appsinter.cine.support.TestUsers.ADMIN;
import static tp.appsinter.cine.support.TestUsers.CUSTOMER;

@ApiIntegrationTest
@DisplayName("Rooms and seat layout")
class SeatLayoutApiTests {

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private ApiClient api;
    @Autowired
    private CatalogueFixture fixture;
    @Autowired
    private UserService userService;

    private Catalogue catalogue;
    private ScreenRoom emptyRoom;

    @BeforeEach
    void setUp() {
        TestUsers.createAll(userService);
        catalogue = fixture.create(SeatType.NORMAL, SeatType.NORMAL);
        emptyRoom = fixture.room("Room 2");
    }

    private static SeatDefinition normal(String row, String column) {
        return new SeatDefinition(row, column, SeatType.NORMAL);
    }

    private MockMvcTester.MockMvcRequestBuilder bulk(String path, BulkSeatsRequestDTO request) {
        return mvc.post().uri(path, emptyRoom.getUuid())
                .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(api.json(request));
    }

    private MockMvcTester.MockMvcRequestBuilder bulk(BulkSeatsRequestDTO request) {
        return bulk("/api/rooms/{id}/seats:bulk", request);
    }

    private MockMvcTester.MockMvcRequestBuilder deleteSeat(Seat seat) {
        return mvc.delete().uri("/api/seats/{id}", seat.getUuid())
                .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN));
    }

    @Nested
    @DisplayName("batch authoring")
    class BatchAuthoring {

        @Test
        @DisplayName("creates a whole grid in one call and points at the resulting layout")
        void generatesGrid() {
            assertThat(mvc.post().uri("/api/rooms/{id}/seats:generate", emptyRoom.getUuid())
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new GenerateSeatsRequestDTO(2, 3, "A", List.of("B"), List.of("A2"), null))))
                    .hasStatus(HttpStatus.CREATED)
                    .hasHeader(HttpHeaders.LOCATION, "/api/rooms/%s/layout".formatted(emptyRoom.getUuid()))
                    .bodyJson()
                    .hasPathSatisfying("$.seats", seats -> assertThat(seats).asArray().hasSize(5))
                    .hasPathSatisfying("$.rows", rows -> assertThat(rows).asArray().containsExactly("A", "B"));
        }

        @Test
        @DisplayName("rolls the whole batch back when one position is taken, unless told to skip it")
        void failsOnConflict() {
            assertThat(bulk(new BulkSeatsRequestDTO(List.of(normal("A", "1"), normal("A", "2")), null)))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson().extractingPath("$.seats").asArray().hasSize(2);

            assertThat(bulk(new BulkSeatsRequestDTO(List.of(normal("A", "2"), normal("A", "3")), null)))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("NAME_TAKEN"))
                    .hasPathSatisfying("$.context.conflicts",
                            conflicts -> assertThat(conflicts).asArray().containsExactly("A2"));
            assertThat(mvc.get().uri("/api/rooms/{id}", emptyRoom.getUuid())
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .as("the new seat of the rejected batch was not created")
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.seatCount").isEqualTo(2);

            assertThat(bulk(new BulkSeatsRequestDTO(List.of(normal("A", "1"),
                    new SeatDefinition("A", "3", SeatType.PREMIUM)), SeatConflictPolicy.SKIP)))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson().extractingPath("$.seats").asArray().hasSize(3);
        }

        @Test
        @DisplayName("answers on the path without the colon too")
        void bulkAlias() {
            assertThat(bulk("/api/rooms/{id}/seats/bulk", new BulkSeatsRequestDTO(List.of(normal("A", "1")), null)))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson().extractingPath("$.seats").asArray().hasSize(1);
        }

        @Test
        @DisplayName("validates every row name in the batch")
        void validatesRows() {
            assertThat(bulk(new BulkSeatsRequestDTO(List.of(normal("A", "1"), normal("1", "2")), null)))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("VALIDATION_FAILED"))
                    .hasPathSatisfying("$.violations[0].code", code -> assertThat(code).isEqualTo("SeatRow"));
        }
    }

    @Nested
    @DisplayName("single seats")
    class SingleSeats {

        @Test
        @DisplayName("reports a taken position with the same shape as the batch")
        void positionTaken() {
            assertThat(mvc.post().uri("/api/seats")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new SeatRequestDTO(catalogue.room().getUuid(), "a", "1", SeatType.NORMAL))))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("NAME_TAKEN"))
                    .hasPathSatisfying("$.context.conflicts",
                            conflicts -> assertThat(conflicts).asArray().containsExactly("A1"));
        }

        @Test
        @DisplayName("refuses to delete a seat that was sold or is being held, and deletes a free one")
        void deletesOnlyFreeSeats() {
            assertThat(mvc.post().uri("/api/reservations")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new ReservationRequestDTO(catalogue.screening().getUuid(),
                                    List.of(catalogue.seat(1).getUuid()), List.of()))))
                    .hasStatus(HttpStatus.CREATED);
            assertThat(mvc.post().uri("/api/screenings/{id}/holds", catalogue.screening().getUuid())
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new SeatHoldRequestDTO(List.of(catalogue.seat(2).getUuid())))))
                    .hasStatus(HttpStatus.CREATED);

            assertThat(deleteSeat(catalogue.seat(1)))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("RESOURCE_IN_USE"))
                    .hasPathSatisfying("$.context.referencedBy", by -> assertThat(by).isEqualTo("Ticket"));
            assertThat(deleteSeat(catalogue.seat(2)))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson()
                    .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("RESOURCE_IN_USE"))
                    .hasPathSatisfying("$.context.referencedBy", by -> assertThat(by).isEqualTo("Seat hold"));

            Seat free = fixture.seat(catalogue.room(), "B", "1", SeatType.NORMAL);
            assertThat(deleteSeat(free)).hasStatus(HttpStatus.NO_CONTENT);
        }
    }

    @Nested
    @DisplayName("rooms")
    class Rooms {

        @Test
        @DisplayName("treats room names as case-insensitive")
        void caseInsensitiveNames() {
            assertThat(mvc.post().uri("/api/rooms")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(api.json(new RoomRequestDTO("ROOM 1", ScreenRoomType.NORMAL))))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("NAME_TAKEN");
        }

        @Test
        @DisplayName("reports each room's seat count in the listing")
        void countsSeats() {
            assertThat(mvc.get().uri("/api/rooms").param("sort", "roomName")
                            .header(HttpHeaders.AUTHORIZATION, api.bearer(ADMIN)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.content[*].seatCount",
                            counts -> assertThat(counts).asArray().containsExactly(2, 0));
        }
    }
}
