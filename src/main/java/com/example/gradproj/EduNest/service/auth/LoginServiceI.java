package com.example.gradproj.EduNest.service.auth;

import com.example.gradproj.EduNest.dto.auth.LoginRequestDto;
import com.example.gradproj.EduNest.service.jwt.JwtServiceI;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginServiceI implements LoginService {
    private final AuthenticationManager authenticationManager;
    private final JwtServiceI jwtService;

    @Override
    public String loginProcess(LoginRequestDto loginRequestDto) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                loginRequestDto.getEmail(), loginRequestDto.getPassword()
        );
        Authentication auth = authenticationManager.authenticate(authentication);
        if(auth == null || !auth.isAuthenticated()) {
            throw new BadCredentialsException("Authentication Failed, Invalid username or password");
        }
        SecurityContextHolder.getContext().setAuthentication(auth);
        String jwt = jwtService.generateToken();
        return jwt;
    }
}
