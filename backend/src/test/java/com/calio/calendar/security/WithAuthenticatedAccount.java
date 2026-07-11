package com.calio.calendar.security;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithSecurityContext;

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithAuthenticatedAccountSecurityContextFactory.class)
public @interface WithAuthenticatedAccount {
}
