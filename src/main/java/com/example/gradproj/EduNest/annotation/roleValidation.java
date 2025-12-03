package com.example.gradproj.EduNest.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class roleValidation implements ConstraintValidator<roleValidator,Long> {


    @Override
    public boolean isValid(Long role, ConstraintValidatorContext context) {
        if(role==null)
            return false;
        return role.intValue() == 2 || role.intValue() == 3;
    }

}
