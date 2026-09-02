package tp.appsinter.cine.model.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MovieGenre {
    ACTION("Action"),
    THRILLER("Thriller"),
    TERROR("Terror"),
    ROMANCE("Romance"),
    COMEDY("Comedy"),
    DRAMA("Drama"),
    ANIMATION("Animation"),
    SCI_FI("Sci-Fi");

    private final String displayName;
}
