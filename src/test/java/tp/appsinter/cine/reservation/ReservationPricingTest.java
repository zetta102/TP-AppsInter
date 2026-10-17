package tp.appsinter.cine.reservation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tp.appsinter.cine.room.SeatType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Ticket pricing")
class ReservationPricingTest {

    @ParameterizedTest(name = "{0} seat at base {1} costs {2}")
    @CsvSource(textBlock = """
            NORMAL,  10.00, 10.00
            PREMIUM, 10.00, 15.00
            NORMAL,  7.55,  7.55
            PREMIUM, 7.55,  11.33
            """)
    @DisplayName("applies the seat type multiplier and rounds to two decimals")
    void appliesSeatTypeMultiplier(SeatType seatType, BigDecimal basePrice, BigDecimal expected) {
        assertThat(ReservationService.priceFor(basePrice, seatType)).isEqualByComparingTo(expected);
    }
}
