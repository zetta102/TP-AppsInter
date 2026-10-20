package tp.appsinter.cine.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

import java.time.Year;

public class MaxYearsAheadValidator implements ConstraintValidator<MaxYearsAhead, Integer> {

    private int yearsAhead;

    @Override
    public void initialize(MaxYearsAhead annotation) {
        yearsAhead = annotation.value();
    }

    @Override
    public boolean isValid(@Nullable Integer year, ConstraintValidatorContext context) {
        return year == null
                || year <= Year.now(context.getClockProvider().getClock()).getValue() + yearsAhead;
    }
}
