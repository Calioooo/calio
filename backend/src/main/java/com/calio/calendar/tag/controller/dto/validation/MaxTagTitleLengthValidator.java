package com.calio.calendar.tag.controller.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxTagTitleLengthValidator implements ConstraintValidator<MaxTagTitleLength, String> {

    private int max;

    @Override
    public void initialize(MaxTagTitleLength constraintAnnotation) {
        this.max = constraintAnnotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.codePointCount(0, value.length()) <= max;
    }
}
