package com.example.gradproj.EduNest.config.auth;

import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.service.auth.LoginLockoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * EB-15 / P1-08 / SEL-03 — THE single canonical login-failure contract:
 * every credential-or-account-state failure (unknown email, wrong password,
 * unverified account, deleted account) throws the SAME BadCredentialsException
 * with the SAME text -> the GlobalExceptionHandler serializes the SAME 401
 * envelope (ERR_INVALID_CREDENTIALS, "Invalid email or password."). No
 * status/code/message discriminator remains for account enumeration
 * (byte-identical bodies, verified by EnumerationProbesTest).
 *
 * Server misconfiguration (role data missing) is deliberately NOT folded —
 * it escapes as a runtime exception -> sanitized generic 500 (EB-12 catch-all).
 *
 * Lockout hooks (Omar spec): failures record here; a successful match resets
 * the streak. The gate itself (ERR_ACCOUNT_LOCKED 429) lives in
 * AuthRateLimitFilter, before this provider is reached.
 */
@Component
@RequiredArgsConstructor
public class EduNestAuthenticationProvider implements AuthenticationProvider {

    static final String CANONICAL_FAILURE = "Invalid email or password.";

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final LoginLockoutService lockoutService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String pwd = authentication.getCredentials().toString();

        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException ex) {
            // unknown email — indistinguishable from a wrong password now (SEL-03)
            lockoutService.recordFailure(username);
            throw new BadCredentialsException(CANONICAL_FAILURE);
        } catch (globalLogicEx ex) {
            // unverified / deleted account — account EXISTENCE leaked before (400 + state text)
            lockoutService.recordFailure(username);
            throw new BadCredentialsException(CANONICAL_FAILURE);
        }

        if (passwordEncoder.matches(pwd, userDetails.getPassword())) {
            lockoutService.reset(username);
            return new UsernamePasswordAuthenticationToken(username, pwd, userDetails.getAuthorities());
        }

        lockoutService.recordFailure(username);
        throw new BadCredentialsException(CANONICAL_FAILURE);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return (UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication));
    }
}
