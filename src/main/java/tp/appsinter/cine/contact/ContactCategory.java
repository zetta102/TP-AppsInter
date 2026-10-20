package tp.appsinter.cine.contact;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tp.appsinter.cine.shared.DisplayNamed;

@Getter
@RequiredArgsConstructor
public enum ContactCategory implements DisplayNamed {
    GENERAL("General"),
    RESERVATION("Reservation"),
    COMPLAINT("Complaint"),
    SUGGESTION("Suggestion");

    private final String displayName;
}
