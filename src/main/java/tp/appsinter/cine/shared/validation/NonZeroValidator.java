package tp.appsinter.cine.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

public class NonZeroValidator implements ConstraintValidator<NonZero, Integer> {

    @Override
    public boolean isValid(@Nullable Integer value, ConstraintValidatorContext context) {
        return value == null || value != 0;
    }
}
