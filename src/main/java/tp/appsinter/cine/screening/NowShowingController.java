package tp.appsinter.cine.screening;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tp.appsinter.cine.screening.dto.response.NowShowingResponseDTO;
import tp.appsinter.cine.shared.dto.response.PageResponseDTO;
import tp.appsinter.cine.shared.web.AllowedSort;

import java.time.OffsetDateTime;

/**
 * La cartelera se publica bajo {@code /api/movies} y con la etiqueta de películas, porque así la
 * busca el cliente, pero vive en {@code screening}: la arman las funciones y su disponibilidad, y
 * {@code movie} no depende de {@code screening}.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Movies")
public class NowShowingController {

    private final MovieScreeningService movieScreeningService;

    /**
     * Cartelera agregada: películas vigentes con sus funciones vigentes que empiezan en
     * {@code [from, to]}, en orden cronológico y con disponibilidad. Reemplaza descargar películas y
     * funciones por separado y cruzarlas en el cliente.
     *
     * @param from desde cuándo; por omisión, ahora
     * @param to   hasta cuándo; por omisión, sin límite
     */
    @GetMapping("/api/movies/now-showing")
    public PageResponseDTO<NowShowingResponseDTO> nowShowing(
            @Nullable Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable OffsetDateTime to,
            @ParameterObject @AllowedSort({"movieName", "releaseYear"}) Pageable pageable) {
        return movieScreeningService.nowShowing(from, to,
                authentication == null ? null : authentication.getName(), pageable);
    }
}
