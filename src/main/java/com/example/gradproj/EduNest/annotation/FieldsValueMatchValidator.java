package com.example.gradproj.EduNest.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

public class FieldsValueMatchValidator implements ConstraintValidator<FieldsValueMatch, Object> {

    private String field;
    private String fieldMatch;

    @Override
    public void initialize(FieldsValueMatch constraintAnnotation) {
        this.field = constraintAnnotation.field();
        this.fieldMatch = constraintAnnotation.fieldMatch();
    }


    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        Object fieldValue = new BeanWrapperImpl(value).getPropertyValue(field);
        Object fieldMatchValue = new BeanWrapperImpl(value).getPropertyValue(fieldMatch);

        boolean isValid = false;
        if (fieldValue != null) {
            isValid = fieldValue.equals(fieldMatchValue);
        } else {
            isValid = fieldMatchValue == null;
        }

        if (!isValid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(fieldMatch)
                    .addConstraintViolation();
        }

        return isValid;
    }

}