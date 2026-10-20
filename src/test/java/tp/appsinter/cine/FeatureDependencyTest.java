package tp.appsinter.cine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las funcionalidades ({@code auth}, {@code user}, {@code movie}, {@code room}, {@code screening},
 * {@code hold}, {@code reservation}, …) dependen unas de otras en una sola dirección.
 *
 * <p>Cuando una funcionalidad de base necesita algo de otra que depende de ella, declara una interfaz
 * y la otra la implementa (por ejemplo {@code screening.SoldSeats}, que implementa
 * {@code reservation}). Esta prueba lee los {@code import} del código de producción y falla con el
 * recorrido del primer ciclo que encuentre. No ve referencias con el nombre completo sin
 * {@code import}; el código no tiene ninguna.
 */
@DisplayName("Feature dependencies")
class FeatureDependencyTest {

    private static final Path ROOT = Path.of("src", "main", "java", "tp", "appsinter", "cine");
    private static final Pattern IMPORT =
            Pattern.compile("^import\\s+(?:static\\s+)?tp\\.appsinter\\.cine\\.([a-z]+)\\.", Pattern.MULTILINE);

    @Test
    @DisplayName("form no cycle, directly or through other features")
    void noCycles() throws IOException {
        Map<String, Set<String>> graph = dependencies();

        // Comprueba que la lectura funciona: sin esto, un grafo vacío pasaría la prueba.
        assertThat(graph.get("reservation")).contains("screening", "hold", "room", "user");
        assertThat(findCycle(graph)).as("feature dependency cycle").isEmpty();
    }

    private static Map<String, Set<String>> dependencies() throws IOException {
        Map<String, Set<String>> graph = new TreeMap<>();
        List<Path> sources;
        try (Stream<Path> files = Files.walk(ROOT)) {
            sources = files.filter(file -> file.toString().endsWith(".java")).toList();
        }
        for (Path file : sources) {
            Path relative = ROOT.relativize(file);
            if (relative.getNameCount() < 2) {
                continue;    // paquete raíz: CineApplication no es una funcionalidad
            }
            String feature = relative.getName(0).toString();
            Set<String> targets = graph.computeIfAbsent(feature, key -> new TreeSet<>());
            Matcher matcher = IMPORT.matcher(Files.readString(file));
            while (matcher.find()) {
                if (!matcher.group(1).equals(feature)) {
                    targets.add(matcher.group(1));
                }
            }
        }
        return graph;
    }

    /**
     * Recorrido en profundidad. Devuelve el primer ciclo, cerrado sobre sí mismo
     * (por ejemplo {@code [movie, screening, movie]}), o una lista vacía.
     */
    private static List<String> findCycle(Map<String, Set<String>> graph) {
        Set<String> done = new HashSet<>();
        for (String start : graph.keySet()) {
            List<String> cycle = visit(start, graph, new ArrayList<>(), done);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return List.of();
    }

    private static List<String> visit(String node, Map<String, Set<String>> graph, List<String> path,
                                      Set<String> done) {
        int seen = path.indexOf(node);
        if (seen >= 0) {
            List<String> cycle = new ArrayList<>(path.subList(seen, path.size()));
            cycle.add(node);
            return cycle;
        }
        if (done.contains(node)) {
            return List.of();
        }
        path.add(node);
        for (String next : graph.getOrDefault(node, Set.of())) {
            List<String> cycle = visit(next, graph, path, done);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        path.removeLast();
        done.add(node);
        return List.of();
    }
}
