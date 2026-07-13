package com.calio.calendar.security;

import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.account.domain.Account;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithAuthenticatedAccountSecurityContextFactory
        implements WithSecurityContextFactory<WithAuthenticatedAccount> {

    @Autowired
    private AccountRepository accountRepository;

    @Override
    public SecurityContext createSecurityContext(WithAuthenticatedAccount annotation) {
        Account account = accountRepository.saveAndFlush(new Account());
        AuthenticatedAccount principal = new AuthenticatedAccount(account.getId());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        return context;
    }
}
