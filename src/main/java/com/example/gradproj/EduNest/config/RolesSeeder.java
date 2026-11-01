package com.example.gradproj.EduNest.config;

import com.example.gradproj.EduNest.entity.Roles;
import com.example.gradproj.EduNest.repository.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Slf4j
@Configuration
public class RolesSeeder {
    @Bean
    public CommandLineRunner initRoles(RoleRepository roleRepository) {
        return args -> {
            String[] roles = {"ADMIN", "MENTOR", "STUDENT"};

            Arrays.stream(roles).forEach(roleName -> {
                if (roleRepository.findByName(roleName).isEmpty()) {
                    Roles role = Roles.builder()
                            .name(roleName)
                            .build();
                    roleRepository.save(role);
                    log.info("Role created: {}", roleName);
                }
            });
        };
    }
}
