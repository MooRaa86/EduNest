package com.example.gradproj.EduNest.config.auth;

import com.example.gradproj.EduNest.entity.users.Admin;
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
                throw new globalLogicEx("User has no role assigned!");
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
                throw new globalLogicEx("Admin has no role assigned!");
            }
            String roleName = "ROLE_" + admin.getRole().getName();
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(roleName));
            return new User(admin.getEmail(), admin.getPassword(), authorities);
        }

        throw new UsernameNotFoundException("User not found for this email : " + username);
    }

}
