package com.example.gradproj.EduNest.config.auth;

import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
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

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        AuthUserProjection user =
                userRepository.findAuthUser(username)
                        .orElseThrow(() ->
                                new UsernameNotFoundException("User not found for this email : " + username));

        if (user.getRoleName() == null) {
            throw new globalLogicEx("User has no role assigned!");
        }
        if(Boolean.FALSE.equals(user.getEnabled())){
            throw new globalLogicEx("This account is not verified, request a new otp...");
        }
        if(Boolean.TRUE.equals(user.getDeleted())){
            throw new globalLogicEx("This User is deleted! Contact us..");
        }
        String roleName = "ROLE_" + user.getRoleName();
        List<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority(roleName));
        return new User(user.getEmail(),user.getPassword(),authorities);
    }

}
