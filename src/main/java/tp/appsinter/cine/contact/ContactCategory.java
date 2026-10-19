package tp.appsinter.cine.contact;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ContactCategory {
    GENERAL("General"),
    RESERVATION("Reservation"),
    COMPLAINT("Complaint"),
    SUGGESTION("Suggestion");

    private final String displayName;
}
