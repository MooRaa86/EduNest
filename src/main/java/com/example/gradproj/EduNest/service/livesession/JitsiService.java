package com.example.gradproj.EduNest.service.livesession;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class JitsiService {

    public String createRoomLink(Long sessionId) {
        String jitsiBaseUrl = "https://meet.jit.si";
        return jitsiBaseUrl + "/EduNest_Session_" + sessionId + "_"+ UUID.randomUUID().toString();
    }

}
