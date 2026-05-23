package com.example.gradproj.EduNest.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/mentorship-cover-images/**")
                .addResourceLocations("file:uploads/mentorship-cover-images/");
        registry.addResourceHandler("/uploads/profile-images/**")
                .addResourceLocations("file:uploads/profile-images/");
        registry.addResourceHandler("/uploads/student-profile/**")
                .addResourceLocations("file:uploads/student-profile/");
        registry.addResourceHandler("/uploads/mentor-profile/**")
                .addResourceLocations("file:uploads/mentor-profile/");
    }
}