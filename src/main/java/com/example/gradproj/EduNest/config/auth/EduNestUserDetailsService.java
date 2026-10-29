package com.example.gradproj.EduNest.config.auth;

import com.example.gradproj.EduNest.entity.users.Admin;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.users.AdminRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.repository.users.projection.AuthUserProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EduNestUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // First try to find in UserRepository (Students, Instructors, etc.)
        AuthUserProjection user = userRepository.findAuthUser(username).orElse(null);

        if (user != null) {
            if (user.getRoleName() == null) {
                // EB-15: a server-data misconfiguration, NOT a credential fact.
                // Escapes as a runtime exception -> ProviderManager passes it
                // through -> EB-12 catch-all -> sanitized generic 500 (message
                // logged with refId, never sent to the client).
                throw new IllegalStateException("Auth data inconsistency: account has no role assigned");
            }
            if (Boolean.FALSE.equals(user.getEnabled())) {
                throw new globalLogicEx("This account is not verified, request a new otp...");
            }
            if (Boolean.TRUE.equals(user.getDeleted())) {
                throw new globalLogicEx("This User is deleted! Contact us..");
            }
            String roleName = "ROLE_" + user.getRoleName();
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(roleName));
            return new User(user.getEmail(), user.getPassword(), authorities);
        }

        // If not found in UserRepository, try AdminRepository
        Admin admin = adminRepository.findByEmail(username).orElse(null);

        if (admin != null) {
            if (admin.getRole() == null) {
                // EB-15: same misconfiguration discipline as above — generic 500
                throw new IllegalStateException("Auth data inconsistency: admin has no role assigned");
            }
            String roleName = "ROLE_" + admin.getRole().getName();
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(roleName));
            return new User(admin.getEmail(), admin.getPassword(), authorities);
        }

        throw new UsernameNotFoundException("User not found for this email : " + username);
    }

}
