package tp.appsinter.cine.room;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeatType {
    NORMAL("Normal"),
    PREMIUM("Premium");

    private final String seatName;
}
