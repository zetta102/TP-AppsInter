package tp.appsinter.cine.movie.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.screening.dto.MovieScreeningResponseDTO;

import java.util.List;

/**
 * Una película de la cartelera con sus funciones dentro de la ventana pedida, en orden cronológico
 * y con la disponibilidad ya calculada.
 */
@Schema(name = "NowShowing")
public record NowShowingResponseDTO(
        MovieResponseDTO movie,
        List<MovieScreeningResponseDTO> screenings
) {

    public NowShowingResponseDTO {
        screenings = List.copyOf(screenings);
    }
}
