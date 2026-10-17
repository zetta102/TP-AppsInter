package tp.appsinter.cine.screening.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import tp.appsinter.cine.movie.dto.response.MovieResponseDTO;

import java.util.List;

/**
 * Una película de la cartelera con sus funciones dentro de la ventana pedida, en orden cronológico
 * y con la disponibilidad ya calculada.
 */
@Schema(name = "NowShowing")
public record NowShowingResponseDTO(
        MovieResponseDTO movie,
        List<ScreeningResponseDTO> screenings
) {

    public NowShowingResponseDTO {
        screenings = List.copyOf(screenings);
    }
}
