package tp.appsinter.cine.model.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Seat coordinate ordering")
class SeatCoordinateComparatorTest {

    @ParameterizedTest(name = "{0} sorts before {1}")
    @CsvSource({
            "A2, A10",
            "A, B",
            "1, 2",
            "2, 10",
            "B1, B2"
    })
    @DisplayName("orders numeric segments by value rather than lexicographically")
    void ordersNumericSegmentsByValue(String smaller, String larger) {
        assertThat(SeatCoordinateComparator.INSTANCE.compare(smaller, larger)).isNegative();
        assertThat(SeatCoordinateComparator.INSTANCE.compare(larger, smaller)).isPositive();
    }

    @Test
    @DisplayName("sorts a mixed row of seat columns naturally")
    void sortsNaturally() {
        List<String> sorted = Stream.of("10", "2", "1", "21", "3")
                .sorted(SeatCoordinateComparator.INSTANCE)
                .toList();

        assertThat(sorted).containsExactly("1", "2", "3", "10", "21");
    }

    @Test
    @DisplayName("treats identical coordinates as equal")
    void treatsIdenticalAsEqual() {
        assertThat(SeatCoordinateComparator.INSTANCE.compare("A10", "A10")).isZero();
    }
}
