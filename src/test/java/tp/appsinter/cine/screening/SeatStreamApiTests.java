package tp.appsinter.cine.screening;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tp.appsinter.cine.hold.dto.request.SeatHoldRequestDTO;
import tp.appsinter.cine.room.SeatType;
import tp.appsinter.cine.support.ApiClient;
import tp.appsinter.cine.support.ApiIntegrationTest;
import tp.appsinter.cine.support.CatalogueFixture;
import tp.appsinter.cine.support.CatalogueFixture.Catalogue;
import tp.appsinter.cine.support.TestUsers;
import tp.appsinter.cine.user.UserService;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static tp.appsinter.cine.support.TestUsers.CUSTOMER;

@ApiIntegrationTest
@DisplayName("Seat availability stream")
class SeatStreamApiTests {

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private ApiClient api;
    @Autowired
    private CatalogueFixture fixture;
    @Autowired
    private UserService userService;
    @Autowired
    private SeatStreamService seatStreamService;

    private Catalogue catalogue;

    @BeforeEach
    void setUp() {
        TestUsers.createAll(userService);
        catalogue = fixture.create(SeatType.NORMAL);
    }

    @Test
    @DisplayName("pushes a seats event to an anonymous subscriber and forgets it once it disconnects")
    void pushesHoldEvents() {
        UUID screeningId = catalogue.screening().getUuid();
        // El canal no termina solo: asyncExchange no espera el resultado asíncrono, que es lo que
        // haría exchange.
        MvcTestResult stream = mvc.get().uri("/api/screenings/{id}/seats/stream", screeningId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .asyncExchange();
        assertThat(stream).request().hasAsyncStarted(true);
        await().atMost(Duration.ofSeconds(2)).until(() -> seatStreamService.subscriberCount(screeningId) == 1);

        assertThat(mvc.post().uri("/api/screenings/{id}/holds", screeningId)
                        .header(HttpHeaders.AUTHORIZATION, api.bearer(CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(new SeatHoldRequestDTO(List.of(catalogue.seat(1).getUuid())))))
                .hasStatus(HttpStatus.CREATED);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(stream.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("event:seats")
                        .contains("\"seatId\":\"" + catalogue.seat(1).getUuid() + "\"")
                        .contains("\"available\":false")
                        .contains("\"held\":true"));

        // Cortar la conexión del lado del contenedor tiene que dar de baja al suscriptor.
        stream.getRequest().getAsyncContext().complete();
        await().atMost(Duration.ofSeconds(2)).until(() -> seatStreamService.subscriberCount(screeningId) == 0);
    }

    @Test
    @DisplayName("answers 404 before opening a stream for an unknown screening")
    void unknownScreening() {
        assertThat(mvc.get().uri("/api/screenings/{id}/seats/stream", UUID.randomUUID())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
