package com.calio.calendar.tag.controller.dto.validation;

import com.calio.calendar.tag.domain.TagTitle;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = MaxTagTitleLengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxTagTitleLength {

    String message() default "태그 제목은 최대 20자까지 입력할 수 있습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int max() default TagTitle.MAX_TAG_TITLE_LENGTH;
}
