package com.example.gradproj.EduNest.service.livesession;

import com.example.gradproj.EduNest.config.livesession.AgoraConfig;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import io.agora.media.RtcTokenBuilder2;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AgoraService {

    private final AgoraConfig agoraConfig;

    public String createChannelName(Long sessionId){
        return "session_" + sessionId;
    }

    public String generateToken(String channelName){

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserEntity user = (UserEntity) authentication.getPrincipal();
        int userId = user.getId().intValue();

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();
        int currentTime = (int) (Instant.now().getEpochSecond());
        int expirationTime = currentTime+(agoraConfig.getTokenExpireSeconds());

return tokenBuilder.buildTokenWithUid(agoraConfig.getAppId(),
        agoraConfig.getAppCertificate(),
        channelName,
         userId,
        RtcTokenBuilder2.Role.ROLE_PUBLISHER,
        expirationTime,
        expirationTime);
    }
}
