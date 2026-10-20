package tp.appsinter.cine.room;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tp.appsinter.cine.shared.DisplayNamed;

@Getter
@RequiredArgsConstructor
public enum ScreenRoomType implements DisplayNamed {
    NORMAL("Normal"),
    THREED("3D"),
    FOURD("4D"),
    IMAX("IMAX");

    private final String displayName;
}
