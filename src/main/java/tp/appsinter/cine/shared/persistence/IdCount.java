package tp.appsinter.cine.shared.persistence;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Una fila de una consulta agrupada: un identificador y cuántas filas le corresponden.
 *
 * <p>Las consultas lo construyen con {@code select new tp.appsinter.cine.shared.persistence.IdCount(…)}
 * en lugar de devolver {@code Object[]}, así quien las usa no tiene que convertir columnas a mano.
 */
public record IdCount(UUID id, long count) {

    /**
     * Las filas de una consulta agrupada como mapa consultable. Un identificador ausente cuenta cero.
     */
    public static Map<UUID, Integer> toMap(List<IdCount> rows) {
        return rows.stream().collect(Collectors.toMap(IdCount::id, row -> Math.toIntExact(row.count())));
    }
}
