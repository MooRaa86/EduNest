package com.example.gradproj.EduNest.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RoleValidation.class)
@Documented
public @interface RoleValidator {
    String message() default "Role id must be 2 for mentor and 3 for student";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

}
