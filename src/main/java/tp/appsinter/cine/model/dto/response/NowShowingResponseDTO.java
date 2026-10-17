package tp.appsinter.cine.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Una película de la cartelera con sus funciones dentro de la ventana pedida, en orden cronológico
 * y con la disponibilidad ya calculada.
 */
@Schema(name = "NowShowing")
public record NowShowingResponseDTO(
        CreateMovieResponseDTO movie,
        List<CreateMovieScreeningResponseDTO> screenings
) {

    public NowShowingResponseDTO {
        screenings = List.copyOf(screenings);
    }
}
