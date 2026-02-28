package com.example.gradproj.EduNest.validation;

import com.example.gradproj.EduNest.dto.auth.RegisterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, RegisterRequest> {
    @Override
    public boolean isValid(RegisterRequest value, ConstraintValidatorContext context) {
        if (value == null) return true;
        String pass = value.getPassword();
        String confirm = value.getConfirmPassword();
        if (pass == null || confirm == null) return false;
        return pass.equals(confirm);
    }
}
