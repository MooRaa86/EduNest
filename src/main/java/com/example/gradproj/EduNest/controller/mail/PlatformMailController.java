package com.example.gradproj.EduNest.controller.mail;

import com.example.gradproj.EduNest.dto.mail.MailRequest;
import com.example.gradproj.EduNest.service.register.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("/mail")
@RequiredArgsConstructor
public class PlatformMailController {
    private final EmailService emailService;

    @PostMapping("/send")
    public ResponseEntity<String> sendEmail(
            @RequestBody MailRequest request
    ) {

        String template =
                emailService.getEmailTemplate("general-mail.html");

        String html = template
                .replace("{{name}}", request.getName())
                .replace("{{message}}", request.getMessage());

        emailService.sendEmail(
                request.getEmail(),
                "EduNest Message",
                html
        );

        return ResponseEntity.ok("Email sent successfully");
    }

}
