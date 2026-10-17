package tp.appsinter.cine.screening;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tp.appsinter.cine.movie.MovieScreenings;
import tp.appsinter.cine.room.RoomScreenings;

/**
 * Responde a {@code movie} y {@code room} sobre sus funciones, sin que esas funcionalidades tengan que
 * conocer los repositorios de {@code screening}.
 *
 * <p>Son dos beans y no una clase que implemente ambas interfaces porque las dos tienen la misma
 * firma con distinto significado: una recibe la película y la otra la sala.
 */
@Configuration(proxyBeanMethods = false)
class ScreeningLookups {

    @Bean
    MovieScreenings movieScreenings(MovieScreeningRepository movieScreeningRepository) {
        return movieScreeningRepository::existsByMovieUuidAndArchivedAtIsNullAndEndsAtAfter;
    }

    @Bean
    RoomScreenings roomScreenings(MovieScreeningRepository movieScreeningRepository) {
        return movieScreeningRepository::existsByScreenRoomUuidAndArchivedAtIsNullAndEndsAtAfter;
    }
}
