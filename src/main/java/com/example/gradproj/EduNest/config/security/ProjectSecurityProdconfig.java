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
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-resources/**",
                                "/api/auth/**",
                                "/ws/**",
                                "/api/v1/contact/**"
                        ).permitAll()
                        .requestMatchers("/api/v1/register/**").permitAll()
                        .requestMatchers("/login-api","/forget-password/**").permitAll()
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
        AuthenticationProvider authProvider = new EduNestAuthenticationProvider(userDetailsService, passwordEncoder,userRepository);
        ProviderManager providerManager = new ProviderManager(authProvider);
        providerManager.setEraseCredentialsAfterAuthentication(false);
        return providerManager;
    }


//    @Bean
//    public CorsConfigurationSource corsConfigurationSource() {
//        CorsConfiguration config = new CorsConfiguration();
//
//        config.setAllowedOriginPatterns(List.of("*"));
//        config.setAllowedMethods(List.of("*"));
//        config.setAllowedHeaders(List.of("*"));
//        config.setAllowCredentials(false);
//
//        UrlBasedCorsConfigurationSource source =
//                new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", config);
//
//        return source;
//    }
}
