package tp.appsinter.cine.reservation.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Reservation request")
class CreateReservationRequestDTOTest {

    private static final UUID SCREENING = UUID.randomUUID();
    private static final UUID SEAT = UUID.randomUUID();
    private static final UUID POPCORN = UUID.randomUUID();

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("merges repeated products into one line")
    void mergesRepeatedProducts() {
        var request = new CreateReservationRequestDTO(SCREENING, List.of(SEAT), List.of(
                new ReservationItemRequestDTO(POPCORN, 2),
                new ReservationItemRequestDTO(POPCORN, 3)));

        assertThat(request.products()).containsExactly(new ReservationItemRequestDTO(POPCORN, 5));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("keeps an invalid line so validation rejects it instead of dropping it")
    void rejectsInvalidLines() {
        var withNull = new CreateReservationRequestDTO(SCREENING, List.of(SEAT), Arrays.asList(
                new ReservationItemRequestDTO(POPCORN, 2), null));
        var withMissingQuantity = new CreateReservationRequestDTO(SCREENING, List.of(SEAT), List.of(
                new ReservationItemRequestDTO(POPCORN, null)));

        assertThat(validator.validate(withNull)).isNotEmpty();
        assertThat(validator.validate(withMissingQuantity)).isNotEmpty();
    }

    @Test
    @DisplayName("does not let a negative line hide inside a merged total")
    void rejectsNegativeLines() {
        var request = new CreateReservationRequestDTO(SCREENING, List.of(SEAT), List.of(
                new ReservationItemRequestDTO(POPCORN, 5),
                new ReservationItemRequestDTO(POPCORN, -3)));

        assertThat(validator.validate(request)).isNotEmpty();
    }
}
