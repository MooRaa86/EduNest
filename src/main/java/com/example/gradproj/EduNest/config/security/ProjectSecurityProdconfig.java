package com.example.gradproj.EduNest.config.security;

import com.example.gradproj.EduNest.config.auth.EduNestAuthenticationProvider;
import com.example.gradproj.EduNest.exception.authHandling.EduNestAccessDeniedHandler;
import com.example.gradproj.EduNest.exception.authHandling.EduNestAuthenticationEntryPoint;
import com.example.gradproj.EduNest.filters.JwtTokenGeneratorFilter;
import com.example.gradproj.EduNest.filters.JwtTokenValidatorFilter;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Profile("prod")
@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
public class ProjectSecurityProdconfig {

    private final JwtTokenGeneratorFilter jwtTokenGeneratorFilter;
    private final JwtTokenValidatorFilter jwtTokenValidatorFilter;
    private final EduNestAuthenticationEntryPoint customAuthEntryPoint;
    private final EduNestAccessDeniedHandler customAccessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)throws Exception{
        http
                .sessionManagement(smc
                        -> smc.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(corsConfig
                        -> corsConfig.configurationSource(new CorsConfigurationSource() {
                    @Override
                    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
                        CorsConfiguration config = new CorsConfiguration();
                        config.setAllowedOriginPatterns(Arrays.asList("*"));
                        config.setAllowedMethods(Collections.singletonList("*"));
                        config.setAllowCredentials(true);
                        config.setAllowedHeaders(Collections.singletonList("*"));
                        config.setExposedHeaders(Arrays.asList("Authorization"));
                        config.setMaxAge(3600L);
                        return config;
                    }
                }))
                .csrf(csrf->csrf.disable())
                .addFilterAfter(jwtTokenGeneratorFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(jwtTokenValidatorFilter, BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth->auth
                        // ========== Public APIs ==========
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/api/auth/**",
                                "/api/v1/register/**",
                                "/login-api",
                                "/forget-password/**",
                                "/api/v1/admin/register",
                                "/ws/**",
                                "/uploads/mentorship-cover-images/**",
                                "/uploads/profile-images/**",
                                "/uploads/student-profile/**",
                                "/uploads/mentor-profile/**",
                                "/uploads/chatroom/**",
                                "/api/v1/contact/save-message",
                                "/api/v1/student/mentorships/*/overview",
                                "/api/v1/student/mentorships/*/reviews",
                                "/api/v1/mentorship/explore",
                                "/api/v1/mentorship/categories",
                                "/api/v1/profile/mentor/**",
                                "uploads/**"
                        ).permitAll()
                        // ========== ADMIN Only ==========
                        .requestMatchers(
                                "/api/v1/admin/**",
                                "/admin/**",
                                "/api/admin/**",
                                "/api/users/*/badges",
                                "/api/v1/contact/all-messages",
                                "/api/v1/contact/message/**",
                                "/api/v1/contact/messages/**"
                        ).hasRole("ADMIN")

//                        // ========== MENTOR Only ==========
//                        .requestMatchers(
//                                "/api/v1/dashboard/**",
//                                "/mentor/**",
//                                "/profile/students/**",
//                                "/lectures/**",
//                                "/api/v1/week/**",
//                                "/api/v1/task/**",
//                                "/api/v1/task-submission/*/grade",
//                                "/api/v1/project/**",
//                                "/api/v1/project/submissions/*/grade",
//                                "/api/v1/quiz/**",
//                                "/api/v1/question/**",
//                                "/api/v1/answer/**",
//                                "/api/v1/submissions/quiz/**",
//                                "/api/v1/badges/mentorship/**",
//                                "/api/v1/badges/*",
//                                "/api/v1/badge-awards/**",
//                                "/api/v1/liveSession/**"
//                        ).hasRole("MENTOR")
//
//                        // ========== STUDENT Only ==========
//                        .requestMatchers(
//                                "/student/**",
//                                "/api/v1/my-learning",
//                                "/api/v1/homepage/**",
//                                "/api/v1/student/**",
//                                "/api/v1/mentorship/*/join",
//                                "/api/v1/mentorship/*/rate",
//                                "/api/v1/task-submission/**",
//                                "/api/v1/project/*/submissions",
//                                "/api/v1/submit-quiz-answer/**",
//                                "/api/v1/submissions/student/**",
//                                "/api/v1/liveSession/join/**",
//                                "/api/v1/liveSession/myAttendance/**",
//                                "/api/v1/liveSession/student/**",
//                                "/api/v1/chat-room/*/join"
//                        ).hasRole("STUDENT")
                        
                        //ToDo add prefix with rule MENTOR & STUDENT

                        // ========== Authenticated Users (Both MENTOR & STUDENT) ==========
                        .requestMatchers(
                                "/settings/**",
                                "/api/v1/notifications/**",
                                "/student/profile/**",
                                "/api/v1/file/**"
                        ).authenticated()

                        // ========== Any remaining requests require authentication ==========
                        .anyRequest().authenticated()
                );
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(customAuthEntryPoint)
                .accessDeniedHandler(customAccessDeniedHandler)
        );
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            UserRepository userRepository
    ){
        AuthenticationProvider authProvider = new EduNestAuthenticationProvider(userDetailsService, passwordEncoder);
        ProviderManager providerManager = new ProviderManager(authProvider);
        providerManager.setEraseCredentialsAfterAuthentication(false);
        return providerManager;
    }
}
