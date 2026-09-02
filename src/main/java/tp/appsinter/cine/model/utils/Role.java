package tp.appsinter.cine.model.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {
    CUSTOMER("Customer"),
    ADMIN("Admin");

    private final String displayName;

    public String authority() {
        return "ROLE_" + name();
    }
}
