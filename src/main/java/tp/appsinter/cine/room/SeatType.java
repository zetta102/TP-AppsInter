package tp.appsinter.cine.room;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tp.appsinter.cine.shared.DisplayNamed;

@Getter
@RequiredArgsConstructor
public enum SeatType implements DisplayNamed {
    NORMAL("Normal"),
    PREMIUM("Premium");

    private final String displayName;
}
