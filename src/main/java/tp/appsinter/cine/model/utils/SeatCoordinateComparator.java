package tp.appsinter.cine.model.utils;

import java.util.Comparator;

/**
 * Orders seat coordinates the way a human reads them, so {@code A2} comes before {@code A10}.
 */
public final class SeatCoordinateComparator implements Comparator<String> {

    public static final SeatCoordinateComparator INSTANCE = new SeatCoordinateComparator();

    private SeatCoordinateComparator() {
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
