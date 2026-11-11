package com.example.gradproj.EduNest.service.register;

import com.example.gradproj.EduNest.entity.email.FailedEmailAttempt;
import com.example.gradproj.EduNest.entity.email.FailedEmailStatus;
import com.example.gradproj.EduNest.repository.email.FailedEmailAttemptRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * EB-62 / INT-02: Email service with failure observation, recording, and template loading.
 */
@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final FailedEmailAttemptRepository failedEmailRepo;

    @Value("${spring.mail.username:noreply@edunest.com}")
    private String sender;

    @Autowired
    public EmailService(JavaMailSender mailSender, FailedEmailAttemptRepository failedEmailRepo) {
        this.mailSender = mailSender;
        this.failedEmailRepo = failedEmailRepo;
    }

    /**
     * Backward-compatible 1-argument constructor for test stubs / subclasses.
     */
    public EmailService(JavaMailSender mailSender) {
        this(mailSender, null);
    }

    /**
     * Synchronous direct dispatch. Throws MessagingException/MailException on SMTP failure.
     * Used by EmailRetryService and sendEmail.
     */
    public void sendDirect(String to, String subject, String htmlBody) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject(subject);
        helper.setFrom(sender);
        helper.setText(htmlBody, true);

        mailSender.send(message);
    }

    /**
     * Synchronous direct dispatch with attachment.
     */
    public void sendDirectWithAttachment(String to, String subject, String htmlBody,
                                         byte[] attachmentData, String attachmentName) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject(subject);
        helper.setFrom(sender);
        helper.setText(htmlBody, true);
        helper.addAttachment(attachmentName, new ByteArrayResource(attachmentData));

        mailSender.send(message);
    }

    /**
     * Asynchronous fire-and-forget email dispatch with failure recording (EB-62 / INT-02).
     */
    @Async
    public void sendEmail(String to, String subject, String htmlBody) {
        try {
            sendDirect(to, subject, htmlBody);
        } catch (Exception e) {
            log.error("EB-62: SMTP delivery failed for recipient={}: {}", to, e.getMessage());
            recordInitialFailure(to, subject, htmlBody, e);
        }
    }

    /**
     * Asynchronous fire-and-forget email with attachment dispatch with failure recording.
     */
    @Async
    public void sendEmailWithAttachment(String to, String subject, String htmlBody,
                                        byte[] attachmentData, String attachmentName) {
        try {
            sendDirectWithAttachment(to, subject, htmlBody, attachmentData, attachmentName);
        } catch (Exception e) {
            log.error("EB-62: SMTP delivery with attachment failed for recipient={}: {}", to, e.getMessage());
            recordInitialFailure(to, subject, htmlBody, e);
        }
    }

    /**
     * Records an initial delivery failure in the outbox-style persistent table.
     */
    public FailedEmailAttempt recordInitialFailure(String to, String subject, String htmlBody, Throwable t) {
        LocalDateTime now = LocalDateTime.now();
        String errorMsg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
        if (errorMsg.length() > 2000) {
            errorMsg = errorMsg.substring(0, 2000);
        }

        FailedEmailAttempt attempt = FailedEmailAttempt.builder()
                .recipient(to != null ? to : "unknown")
                .subject(subject != null ? subject : "No Subject")
                .htmlBody(htmlBody != null ? htmlBody : "")
                .attemptCount(1)
                .maxAttempts(3)
                .lastError(errorMsg)
                .status(FailedEmailStatus.PENDING)
                .lastAttemptAt(now)
                .nextRetryAt(now.plusMinutes(1))
                .build();

        if (failedEmailRepo == null) {
            log.debug("EB-62: failedEmailRepo is null (stub/test mode), skipping persistence");
            return attempt;
        }

        try {
            return failedEmailRepo.save(attempt);
        } catch (Exception persistenceEx) {
            log.error("EB-62: Failed to persist failed_email_attempt for recipient={}: {}",
                    to, persistenceEx.getMessage(), persistenceEx);
            return attempt;
        }
    }

    public String getEmailTemplate(String templateName) {
        try (InputStream is = getClass()
                .getClassLoader()
                .getResourceAsStream("templates/emails/" + templateName)) {

            if (is == null) {
                throw new RuntimeException("Template not found: " + templateName);
            }

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new RuntimeException("Failed to read email template: " + templateName, e);
        }
    }
}
