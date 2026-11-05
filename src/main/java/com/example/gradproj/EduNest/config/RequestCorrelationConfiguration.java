package com.example.gradproj.EduNest.config;

import com.example.gradproj.EduNest.filters.RequestCorrelationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.EnumSet;

@Configuration(proxyBeanMethods = false)
public class RequestCorrelationConfiguration {
    @Bean
    FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilterRegistration() {
        var registration = new FilterRegistrationBean<>(new RequestCorrelationFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR));
        registration.setAsyncSupported(true);
        return registration;
    }
}
