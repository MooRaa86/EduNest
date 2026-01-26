package com.example.gradproj.EduNest.service.jwt;

import com.example.gradproj.EduNest.exception.jwt.InvalidJwtToken;
import com.example.gradproj.EduNest.utils.Constants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class JwtService implements JwtServiceI{

    private static final String DEFAULT_SECRET = Constants.JWT_SECRET_DEFAULT_VALUE;
    private static final long EXPIRATION_TIME_MS = 24 * (1000 * 60 * 60);

    private final Environment env;

    @Override
    public String generateToken() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new UsernameNotFoundException("No authentication found in SecurityContext");
        }
        if(null == env){
            throw new RuntimeException("No env found in SecurityContext");
        }

        SecretKey secretKey = getSecretKey();

        String username = authentication.getName();
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .issuer("EduNest")
                .subject("JWT Token")
                .issuedAt(new Date())
                .expiration(new Date(new Date().getTime() + EXPIRATION_TIME_MS))
                .claim("username", username)
                .claim("authorities", authorities)
                .signWith(secretKey)
                .compact();
    }

    @Override
    public void validateToken(String token) {
        if(env == null){
            throw new RuntimeException("No env found in SecurityContext");
        }
        SecretKey secretKey = getSecretKey();
        Claims claims = Jwts.parser().verifyWith(secretKey)
                .build().parseSignedClaims(token).getPayload();
        String username = String.valueOf(claims.get("username"));
        String authorities = String.valueOf(claims.get("authorities"));
        Authentication authentication = new UsernamePasswordAuthenticationToken(username,null,
                AuthorityUtils.commaSeparatedStringToAuthorityList(authorities));
        SecurityContextHolder.getContext().setAuthentication(authentication);


    }

    @Override
    public SecretKey getSecretKey() {
        String secret = env.getProperty(Constants.JWT_SECRET_KEY, DEFAULT_SECRET);
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

}
