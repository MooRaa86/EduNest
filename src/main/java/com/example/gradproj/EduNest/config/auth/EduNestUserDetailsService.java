package com.example.gradproj.EduNest.config.auth;

import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class EduNestUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByEmail(username).orElseThrow(() -> new
                UsernameNotFoundException("There is no details for email : " + username));
        if (user.getRole() == null) {
            throw new globalLogicEx("User has no role assigned!");
        }
        String roleName = "ROLE_" + user.getRole().getName();
        List<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority(roleName));
        return new User(user.getEmail(),user.getPassword(),authorities);
    }

}
