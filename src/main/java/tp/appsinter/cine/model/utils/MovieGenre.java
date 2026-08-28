package tp.appsinter.cine.model.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MovieGenre {
    ACTION,
    THRILLER,
    TERROR,
    ROMANCE;

    private String MovieGenre;
}
