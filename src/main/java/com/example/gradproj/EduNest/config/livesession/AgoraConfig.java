package com.example.gradproj.EduNest.config.livesession;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "agora")
@Data
public class AgoraConfig
{
    private String appId;
    private String appCertificate;
    private int tokenExpireSeconds;
}
