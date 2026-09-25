package com.calio.calendar.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxCodePointLengthValidator
    implements ConstraintValidator<MaxCodePointLength, CharSequence> {

  private int max;

  @Override
  public void initialize(MaxCodePointLength constraintAnnotation) {
    max = constraintAnnotation.max();
  }

  @Override
  public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    String text = value.toString();
    return text.codePointCount(0, text.length()) <= max;
  }
}
