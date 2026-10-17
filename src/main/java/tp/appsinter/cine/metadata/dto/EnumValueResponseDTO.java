package tp.appsinter.cine.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Un valor de enumeración junto con la etiqueta que corresponde mostrar.
 *
 * <p>Las etiquetas se publican sólo en inglés: {@code Accept-Language} se ignora. Traducirlas
 * exigiría un catálogo por idioma que hoy no existe, y devolver el nombre en inglés bajo una
 * cabecera que promete otro idioma sería peor que no prometer nada.
 *
 * @param value valor tal como viaja en el resto de la API
 * @param label texto legible, incluidos los casos que no se deducen del nombre, como {@code THREED}
 *              → {@code 3D}
 */
@Schema(name = "EnumValue")
public record EnumValueResponseDTO(String value, String label) {
}
