package tp.appsinter.cine.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import tp.appsinter.cine.config.SeatHoldProperties;
import tp.appsinter.cine.model.dto.response.EnumValueResponseDTO;
import tp.appsinter.cine.model.dto.response.MetadataConfigResponseDTO;
import tp.appsinter.cine.model.utils.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.zip.CRC32;

/**
 * Metadatos del contrato: los valores de las enumeraciones y las constantes de negocio.
 *
 * <p>Sin esto cada cliente copia las seis listas y sus etiquetas, incluidos los casos que no se
 * deducen del nombre ({@code THREED} → {@code 3D}). Agregar un género en el servidor rompía en
 * silencio el filtro de cada cliente hasta que alguien lo volvía a desplegar.
 *
 * <p>Ambas respuestas son públicas, se cachean por un día y llevan un {@code ETag} derivado del
 * contenido, de modo que un cliente que revalida recibe {@code 304} y nada más.
 */
@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
@Tag(name = "Metadata")
public class MetadataController {

    private static final CacheControl CACHE = CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic();

    private final SeatHoldProperties seatHoldProperties;

    private static <E extends Enum<E>> List<EnumValueResponseDTO> describe(E[] values,
                                                                           Function<E, String> label) {
        return Arrays.stream(values)
                .map(value -> new EnumValueResponseDTO(value.name(), label.apply(value)))
                .toList();
    }

    /**
     * Valores y etiquetas de todas las enumeraciones del contrato.
     */
    @GetMapping("/enums")
    public ResponseEntity<Map<String, List<EnumValueResponseDTO>>> enums(WebRequest request) {
        Map<String, List<EnumValueResponseDTO>> body = new LinkedHashMap<>();
        body.put("role", describe(Role.values(), Role::getDisplayName));
        body.put("movieGenre", describe(MovieGenre.values(), MovieGenre::getDisplayName));
        body.put("screenRoomType", describe(ScreenRoomType.values(), ScreenRoomType::getScreenType));
        body.put("seatType", describe(SeatType.values(), SeatType::getSeatName));
        body.put("productCategory", describe(ProductCategory.values(), ProductCategory::getDisplayName));
        body.put("reservationStatus", describe(ReservationStatus.values(), ReservationStatus::getDisplayName));
        return conditional(request, body, body.toString());
    }

    /**
     * Constantes de negocio que los clientes necesitan para mostrar precios y armar horarios.
     */
    @GetMapping("/config")
    public ResponseEntity<MetadataConfigResponseDTO> config(WebRequest request) {
        MetadataConfigResponseDTO body = new MetadataConfigResponseDTO(
                BusinessConstants.PREMIUM_SEAT_MULTIPLIER,
                BusinessConstants.SCREENING_TURNOVER_MINUTES,
                BusinessConstants.CURRENCY,
                seatHoldProperties.ttl().toSeconds());
        return conditional(request, body, body.toString());
    }

    /**
     * Responde con {@code ETag} y caché, o con {@code 304} si el cliente ya tiene esta versión.
     *
     * <p>El identificador se calcula sobre el propio contenido: cambia exactamente cuando cambia lo
     * que se publica, sin depender de despliegues ni de fechas.
     */
    private <T> ResponseEntity<T> conditional(WebRequest request, T body, String fingerprint) {
        CRC32 crc = new CRC32();
        crc.update(fingerprint.getBytes(StandardCharsets.UTF_8));
        String etag = "\"%s\"".formatted(Long.toHexString(crc.getValue()));
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(304).cacheControl(CACHE).eTag(etag).build();
        }
        return ResponseEntity.ok().cacheControl(CACHE).eTag(etag).body(body);
    }
}
