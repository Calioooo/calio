package com.calio.calendar.security;

import com.calio.calendar.repository.entity.Account;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

public final class TestAccountSupport {

    private TestAccountSupport() {
    }

    public static Account currentAccountReference() {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", currentAccountId());
        return account;
    }

    public static Long currentAccountId() {
        AuthenticatedAccount principal = (AuthenticatedAccount) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return principal.accountId();
    }
}
