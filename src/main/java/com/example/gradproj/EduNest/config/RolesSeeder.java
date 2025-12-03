package com.example.gradproj.EduNest.config;

import com.example.gradproj.EduNest.entity.Roles;
import com.example.gradproj.EduNest.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

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
                    System.out.println("Role created: " + roleName);
                }
            });
        };
    }
}
