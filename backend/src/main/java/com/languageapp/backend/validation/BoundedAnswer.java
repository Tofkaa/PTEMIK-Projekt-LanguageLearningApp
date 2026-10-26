package com.languageapp.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.annotation.*;

@Documented
@Target({ElementType.FIELD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BoundedAnswer.Validator.class)
public @interface BoundedAnswer {
    String message() default "A válasz szöveg legyen, legfeljebb 10 000 karakterrel.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<BoundedAnswer, Object> {
        @Override public boolean isValid(Object value, ConstraintValidatorContext context) {
            return value == null || AnswerLimits.isValid(value); // @NotNull handles missing values.
        }
    }
}
