package tp.appsinter.cine.room;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record Position(
        @Column(name = "seat_row", nullable = false) String row,
        @Column(name = "seat_column", nullable = false) String column
) {
    public Position {
        if (row == null || row.isBlank()) {
            throw new IllegalArgumentException("Seat row must not be blank");
        }
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException("Seat column must not be blank");
        }
        row = row.trim().toUpperCase();
        column = column.trim();
    }
}
