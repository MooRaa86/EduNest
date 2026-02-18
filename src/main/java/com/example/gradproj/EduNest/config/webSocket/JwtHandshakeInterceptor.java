package com.example.gradproj.EduNest.config.webSocket;

import com.example.gradproj.EduNest.service.jwt.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    public JwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {

        if (request instanceof ServletServerHttpRequest servletRequest) {

            HttpServletRequest httpRequest =
                    servletRequest.getServletRequest();

            String token = httpRequest.getHeader("Authorization");

            if (token == null ) {
                return false;
            }

            if (!jwtService.isTokenValid(token)) {
                return false;
            }

            String username = jwtService.extractUsername(token);
            String authorities = jwtService.extractAuthorities(token);

            attributes.put("username", username);
            attributes.put("authorities", authorities);
        }

        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }
}

