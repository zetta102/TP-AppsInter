package tp.appsinter.cine.inventory;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import tp.appsinter.cine.shared.DisplayNamed;

@Getter
@RequiredArgsConstructor
public enum ProductCategory implements DisplayNamed {
    SNACK("Snack"),
    DRINK("Drink"),
    COMBO("Combo"),
    MERCHANDISE("Merchandise");

    private final String displayName;
}
