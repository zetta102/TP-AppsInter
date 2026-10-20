package tp.appsinter.cine.user;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tp.appsinter.cine.shared.DisplayNamed;

@Getter
@RequiredArgsConstructor
public enum Role implements DisplayNamed {
    CUSTOMER("Customer"),
    ADMIN("Admin");

    private final String displayName;

    public String authority() {
        return "ROLE_" + name();
    }
}
