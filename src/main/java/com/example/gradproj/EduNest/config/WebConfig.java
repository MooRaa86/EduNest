package com.example.gradproj.EduNest.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Removed to prevent static serving of sensitive files
        // registry.addResourceHandler("/uploads/**").addResourceLocations("file:uploads/");
    }
}