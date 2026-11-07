package com.example.gradproj.EduNest.service.auth;

import com.example.gradproj.EduNest.config.auth.PrincipalIdentity;
import com.example.gradproj.EduNest.dto.auth.LoginRequestDto;
import com.example.gradproj.EduNest.dto.auth.TokenPairResponseDto;
import com.example.gradproj.EduNest.service.jwt.JwtServiceI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class LoginServiceI implements LoginService {
    private final AuthenticationManager authenticationManager;
    private final JwtServiceI jwtService;
    private final RefreshTokenService refreshTokenService;

    @Autowired
    public LoginServiceI(AuthenticationManager authenticationManager, JwtServiceI jwtService, RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public LoginServiceI(AuthenticationManager authenticationManager, JwtServiceI jwtService) {
        this(authenticationManager, jwtService, null);
    }

    @Override
    public String loginProcess(LoginRequestDto loginRequestDto) {
        TokenPairResponseDto tokenPair = loginWithRefreshToken(loginRequestDto);
        return tokenPair.getAccessToken();
    }

    @Override
    public TokenPairResponseDto loginWithRefreshToken(LoginRequestDto loginRequestDto) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                loginRequestDto.getEmail(), loginRequestDto.getPassword()
        );
        Authentication auth = authenticationManager.authenticate(authentication);
        if (auth == null || !auth.isAuthenticated()) {
            throw new BadCredentialsException("Authentication Failed, Invalid username or password");
        }
        SecurityContextHolder.getContext().setAuthentication(auth);

        if (auth.getPrincipal() instanceof PrincipalIdentity identity) {
            return refreshTokenService.issueInitialTokens(identity);
        }

        // Fallback for mocks or test environments where principal is email string
        String jwt = jwtService.generateToken();
        String role = jwtService.extractAuthorities(jwt);
        return TokenPairResponseDto.builder()
                .accessToken(jwt)
                .refreshToken(null)
                .role(role)
                .build();
    }

    @Override
    public String getJwtRole(String jwt) {
        return jwtService.extractAuthorities(jwt);
    }
}
