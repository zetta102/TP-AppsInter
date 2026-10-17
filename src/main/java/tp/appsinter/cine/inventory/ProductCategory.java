package tp.appsinter.cine.inventory;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductCategory {
    SNACK("Snack"),
    DRINK("Drink"),
    COMBO("Combo"),
    MERCHANDISE("Merchandise");

    private final String displayName;
}
