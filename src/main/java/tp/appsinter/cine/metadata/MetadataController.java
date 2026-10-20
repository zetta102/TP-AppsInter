package tp.appsinter.cine.metadata;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tp.appsinter.cine.contact.ContactCategory;
import tp.appsinter.cine.hold.SeatHoldProperties;
import tp.appsinter.cine.inventory.ProductCategory;
import tp.appsinter.cine.metadata.dto.response.EnumValueResponseDTO;
import tp.appsinter.cine.metadata.dto.response.MetadataConfigResponseDTO;
import tp.appsinter.cine.movie.MovieGenre;
import tp.appsinter.cine.reservation.ReservationStatus;
import tp.appsinter.cine.room.ScreenRoomType;
import tp.appsinter.cine.room.SeatType;
import tp.appsinter.cine.shared.BusinessConstants;
import tp.appsinter.cine.shared.DisplayNamed;
import tp.appsinter.cine.user.Role;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

/**
 * Metadatos del contrato: los valores de las enumeraciones y las constantes de negocio.
 *
 * <p>Sin esto cada cliente copia las siete listas y sus etiquetas, incluidos los casos que no se
 * deducen del nombre ({@code THREED} → {@code 3D}). Agregar un género en el servidor rompía en
 * silencio el filtro de cada cliente hasta que alguien lo volvía a desplegar.
 *
 * <p>Ambas respuestas son públicas, se cachean por un día y llevan un {@code ETag} derivado del
 * contenido. Spring MVC compara ese {@code ETag} con el {@code If-None-Match} de la petición, así que
 * un cliente que revalida recibe {@code 304} y nada más.
 */
@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
@Tag(name = "Metadata")
public class MetadataController {

    private static final CacheControl CACHE = CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic();

    private final SeatHoldProperties seatHoldProperties;

    private static <E extends Enum<E> & DisplayNamed> List<EnumValueResponseDTO> describe(E[] values) {
        return Arrays.stream(values)
                .map(value -> new EnumValueResponseDTO(value.name(), value.getDisplayName()))
                .toList();
    }

    /**
     * Valores y etiquetas de todas las enumeraciones del contrato.
     */
    @GetMapping("/enums")
    public ResponseEntity<Map<String, List<EnumValueResponseDTO>>> enums() {
        Map<String, List<EnumValueResponseDTO>> body = new LinkedHashMap<>();
        body.put("role", describe(Role.values()));
        body.put("movieGenre", describe(MovieGenre.values()));
        body.put("screenRoomType", describe(ScreenRoomType.values()));
        body.put("seatType", describe(SeatType.values()));
        body.put("productCategory", describe(ProductCategory.values()));
        body.put("reservationStatus", describe(ReservationStatus.values()));
        body.put("contactCategory", describe(ContactCategory.values()));
        return cacheable(body, body.toString());
    }

    /**
     * Constantes de negocio que los clientes necesitan para mostrar precios y armar horarios.
     */
    @GetMapping("/config")
    public ResponseEntity<MetadataConfigResponseDTO> config() {
        MetadataConfigResponseDTO body = new MetadataConfigResponseDTO(
                BusinessConstants.PREMIUM_SEAT_MULTIPLIER,
                BusinessConstants.SCREENING_TURNOVER_MINUTES,
                BusinessConstants.CURRENCY,
                seatHoldProperties.ttl().toSeconds());
        return cacheable(body, body.toString());
    }

    /**
     * Responde con caché y un {@code ETag} calculado sobre el propio contenido: cambia exactamente
     * cuando cambia lo que se publica, sin depender de despliegues ni de fechas.
     */
    private static <T> ResponseEntity<T> cacheable(T body, String fingerprint) {
        CRC32 crc = new CRC32();
        crc.update(fingerprint.getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok()
                .cacheControl(CACHE)
                .eTag(Long.toHexString(crc.getValue()))
                .body(body);
    }
}
