package tp.appsinter.cine.room;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScreenRoomType {
    NORMAL("Normal"),
    THREED("3D"),
    FOURD("4D"),
    IMAX("IMAX");

    private final String screenType;
}
