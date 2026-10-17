package tp.appsinter.cine.room;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Ordena las coordenadas de butaca como las lee una persona, de modo que {@code A2} viene antes que
 * {@code A10}.
 */
public final class SeatCoordinateComparator implements Comparator<String> {

    public static final SeatCoordinateComparator INSTANCE = new SeatCoordinateComparator();

    private SeatCoordinateComparator() {
    }

    /**
     * Valores distintos de una coordenada (fila o columna) de las butacas, en el orden de este
     * comparador.
     */
    public static List<String> distinctSorted(List<Seat> seats, Function<Seat, String> coordinate) {
        return seats.stream()
                .map(coordinate)
                .distinct()
                .sorted(INSTANCE)
                .toList();
    }

    private static int endOfDigits(String value, int from) {
        int index = from;
        while (index < value.length() && Character.isDigit(value.charAt(index))) {
            index++;
        }
        return index;
    }

    @Override
    public int compare(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            char leftChar = left.charAt(leftIndex);
            char rightChar = right.charAt(rightIndex);

            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                int leftEnd = endOfDigits(left, leftIndex);
                int rightEnd = endOfDigits(right, rightIndex);
                int comparison = Long.compare(
                        Long.parseLong(left, leftIndex, leftEnd, 10),
                        Long.parseLong(right, rightIndex, rightEnd, 10));
                if (comparison != 0) {
                    return comparison;
                }
                leftIndex = leftEnd;
                rightIndex = rightEnd;
                continue;
            }

            if (leftChar != rightChar) {
                return Character.compare(leftChar, rightChar);
            }
            leftIndex++;
            rightIndex++;
        }
        return Integer.compare(left.length() - leftIndex, right.length() - rightIndex);
    }
}
