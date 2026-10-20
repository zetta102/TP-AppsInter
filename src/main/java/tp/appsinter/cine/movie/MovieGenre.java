package tp.appsinter.cine.movie;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tp.appsinter.cine.shared.DisplayNamed;

@Getter
@RequiredArgsConstructor
public enum MovieGenre implements DisplayNamed {
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
