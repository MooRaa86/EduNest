package com.example.gradproj.EduNest.service.jwt;

import com.example.gradproj.EduNest.config.auth.PrincipalIdentity;
import com.example.gradproj.EduNest.config.security.JwtSecretProvider;
import com.example.gradproj.EduNest.entity.users.Admin;
import com.example.gradproj.EduNest.exception.jwt.InvalidJwtToken;
import com.example.gradproj.EduNest.repository.users.AdminRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.repository.users.projection.UserFullNameProjection;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JwtService implements JwtServiceI {

    /** EB-91: Short-lived access JWT expiration (15 minutes). */
    public static final long EXPIRATION_TIME_MS = 15 * 60 * 1000L;

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final JwtSecretProvider jwtSecretProvider;

    @Override
    public String generateToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new UsernameNotFoundException("No authentication found in SecurityContext");
        }

        Object principal = authentication.getPrincipal();
        String jti = UUID.randomUUID().toString();

        if (principal instanceof PrincipalIdentity identity) {
            return generateAccessToken(identity, jti);
        }

        SecretKey secretKey = getSecretKey();
        String username = authentication.getName();
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        String fullName;
        String userId;
        UserFullNameProjection user = userRepository.findFullNameByEmail(username).orElse(null);
        if (user != null) {
            fullName = user.getFullName();
            userId = user.getPublicId() != null ? user.getPublicId().toString() : null;
        } else {
            Admin admin = adminRepository.findByEmail(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User or Admin not found"));
            fullName = admin.getFirstName() + " " + admin.getLastName();
            userId = admin.getPublicId() != null ? admin.getPublicId().toString() : null;
        }

        return Jwts.builder()
                .issuer("EduNest")
                .subject("JWT Token")
                .id(jti)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME_MS))
                .claim("userId", userId)
                .claim("username", username)
                .claim("authorities", authorities)
                .claim("fullName", fullName)
                .signWith(secretKey)
                .compact();
    }

    @Override
    public String generateAccessToken(PrincipalIdentity identity, String jti) {
        SecretKey secretKey = getSecretKey();
        String username = identity.email();

        String fullName;
        String userId;
        String authorities;

        if (identity.type() == PrincipalIdentity.Type.USER) {
            UserFullNameProjection userProj = userRepository.findFullNameByEmail(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
            fullName = userProj.getFullName();
            userId = userProj.getPublicId() != null ? userProj.getPublicId().toString() : null;
            authorities = userRepository.findByEmail(username)
                    .map(u -> "ROLE_" + u.getRole().getName())
                    .orElse("ROLE_STUDENT");
        } else {
            Admin admin = adminRepository.findByEmail(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Admin not found: " + username));
            fullName = admin.getFirstName() + " " + admin.getLastName();
            userId = admin.getPublicId() != null ? admin.getPublicId().toString() : null;
            authorities = "ROLE_" + admin.getRole().getName();
        }

        return Jwts.builder()
                .issuer("EduNest")
                .subject("JWT Token")
                .id(jti)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME_MS))
                .claim("userId", userId)
                .claim("username", username)
                .claim("authorities", authorities)
                .claim("fullName", fullName)
                .claim("ownerType", identity.type().name())
                .claim("ownerId", identity.id())
                .signWith(secretKey)
                .compact();
    }

    @Override
    public void validateToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        SecretKey secretKey = getSecretKey();
        Claims claims = Jwts.parser().verifyWith(secretKey)
                .build().parseSignedClaims(token).getPayload();
        String username = String.valueOf(claims.get("username"));

        boolean isValidUser = userRepository.isUserEnabledAndNotDeleted(username).orElse(false);

        if (!isValidUser) {
            boolean isAdmin = adminRepository.existsByEmail(username);
            if (!isAdmin) {
                throw new InvalidJwtToken("Invalid token: user is deactivated or deleted, please login again");
            }
        }

        String authorities = String.valueOf(claims.get("authorities"));
        Authentication authentication = new UsernamePasswordAuthenticationToken(username, null,
                AuthorityUtils.commaSeparatedStringToAuthorityList(authorities));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7).trim();
            }
            SecretKey secretKey = getSecretKey();
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String extractUserEmail(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("username", String.class);
    }

    @Override
    public String extractAuthorities(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("authorities", String.class);
    }

    @Override
    public String getFullName(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("fullName", String.class);
    }

    @Override
    public String extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("userId", String.class);
    }

    @Override
    public String extractJti(String token) {
        Claims claims = extractAllClaims(token);
        return claims.getId();
    }

    @Override
    public String extractOwnerType(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("ownerType", String.class);
    }

    @Override
    public Long extractOwnerId(String token) {
        Claims claims = extractAllClaims(token);
        Object idObj = claims.get("ownerId");
        if (idObj instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    @Override
    public Claims extractAllClaims(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        SecretKey secretKey = getSecretKey();
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public SecretKey getSecretKey() {
        return jwtSecretProvider.getSecretKey();
    }
}
