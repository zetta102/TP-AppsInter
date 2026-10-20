package tp.appsinter.cine.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;
import tp.appsinter.cine.inventory.InventoryRepository;
import tp.appsinter.cine.movie.MovieRepository;
import tp.appsinter.cine.room.ScreenRoomRepository;
import tp.appsinter.cine.room.SeatRepository;
import tp.appsinter.cine.screening.MovieScreeningRepository;

/**
 * Piezas compartidas por las pruebas de integración ({@link ApiIntegrationTest}).
 */
@TestConfiguration(proxyBeanMethods = false)
public class ApiTestConfiguration {

    /**
     * Reemplaza al reloj de la aplicación: todo lo que pide un {@code Clock} recibe éste.
     */
    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }

    @Bean
    ApiClient apiClient(MockMvcTester mvc, JsonMapper jsonMapper) {
        return new ApiClient(mvc, jsonMapper);
    }

    @Bean
    CatalogueFixture catalogueFixture(MovieRepository movieRepository,
                                      ScreenRoomRepository screenRoomRepository,
                                      SeatRepository seatRepository,
                                      MovieScreeningRepository movieScreeningRepository,
                                      InventoryRepository inventoryRepository,
                                      MutableClock clock) {
        return new CatalogueFixture(movieRepository, screenRoomRepository, seatRepository,
                movieScreeningRepository, inventoryRepository, clock);
    }
}
