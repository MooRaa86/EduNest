package com.example.gradproj.EduNest.controller.auth;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.auth.LoginRequestDto;
import com.example.gradproj.EduNest.service.auth.LoginService;
import com.example.gradproj.EduNest.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LoginController {
    private final LoginService loginService;

    @PostMapping("/login-api")
    public ResponseEntity<SimpleResponse> login(
            @RequestBody LoginRequestDto DTO
            ){
        String jwt = loginService.loginProcess(DTO);
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("status","Login successful");
        resp.addMessage("jwt",jwt);
        return ResponseEntity
                .status(HttpStatus.OK)
                .header(Constants.JWT_HEADER,jwt)
                .body(resp);
    }
}
