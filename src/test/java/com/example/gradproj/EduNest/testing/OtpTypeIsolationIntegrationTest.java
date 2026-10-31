package com.example.gradproj.EduNest.testing;

import com.example.gradproj.EduNest.entity.Roles;
import com.example.gradproj.EduNest.entity.register.OTP;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.register.EducationalLevel;
import com.example.gradproj.EduNest.enums.register.OtpType;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.exception.registerExceptions.InvalidOtpException;
import com.example.gradproj.EduNest.exception.registerExceptions.OtpExpiredException;
import com.example.gradproj.EduNest.exception.registerExceptions.UserAlreadyVerifiedException;
import com.example.gradproj.EduNest.repository.OTPRepository;
import com.example.gradproj.EduNest.repository.RoleRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.service.account.SettingsService;
import com.example.gradproj.EduNest.service.register.EmailService;
import com.example.gradproj.EduNest.service.register.RegisterServiceImpl;
import com.example.gradproj.EduNest.service.register.ResetTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OtpTypeIsolationIntegrationTest extends TransactionalIntegrationTestBase {
    private static final String EMAIL = "otp-isolation@example.org";
    private static final String CODE = "123456";
    private static final String OTHER_CODE = "654321";

    @Autowired private RegisterServiceImpl registration;
    @Autowired private SettingsService settings;
    @SpyBean private OTPRepository otps;
    @Autowired private StudentRepository students;
    @Autowired private RoleRepository roles;
    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockBean private EmailService emailService;

    private Student student;

    @Test
    void resetTokenServiceRejectsInvalidLifetimesAndCredentialForms() {
        assertThrows(IllegalArgumentException.class, () -> new ResetTokenService(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ResetTokenService(Duration.ofSeconds(-1)));
        ResetTokenService service = new ResetTokenService(Duration.ofSeconds(7));
        assertEquals(Duration.ofSeconds(7), service.lifetime());
        String first = service.generate();
        String second = service.generate();
        assertNotEquals(first, second);
        assertTrue(service.matches(first, service.digest(first)));
        assertFalse(service.matches(second, service.digest(first)));
        assertFalse(service.matches(first, "123456"));
        assertFalse(service.matches(first + "=", service.digest(first)));
    }

    @Test
    void otpColumnAccommodatesVersionedSha256Digest() throws Exception {
        try (Connection connection = dataSource.getConnection();
             var columns = connection.getMetaData().getColumns(null, null, "otp", "otp_code")) {
            assertTrue(columns.next(), "Hibernate-generated OTP.otp_code must exist");
            assertTrue(columns.getInt("COLUMN_SIZE") >= 67,
                    "OTP.otp_code must hold the v1: prefix and SHA-256 hex digest");
        }
    }

    @BeforeEach
    void setUp() {
        when(emailService.getEmailTemplate(anyString())).thenReturn("{{OTP_CODE}} {{otp}}");
        Roles role = roles.findByName("OTP_TEST_STUDENT")
                .orElseGet(() -> roles.save(new Roles(null, "OTP_TEST_STUDENT")));
        student = students.saveAndFlush(Student.builder()
                .firstName("Test").lastName("Student").email(EMAIL)
                .password("test-password").role(role).educationalLevel(EducationalLevel.FRESH)
                .enabled(true).deleted(false).build());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private OTP seed(OtpType type, String code) {
        return otps.saveAndFlush(OTP.builder().user(student).otpType(type).otpCode(code)
                .expiresAt(LocalDateTime.now().plusMinutes(2)).build());
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, "test", List.of()));
    }

    @Test
    void verifyUserHttpPreservesWrongAndCorrectOtpBehavior() throws Exception {
        student.setEnabled(false);
        students.saveAndFlush(student);
        OTP existing = seed(OtpType.VERIFY, CODE);
        mockMvc.perform(post("/api/v1/register/verify-user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"otp\":\"654321\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("ERR_INVALID_OTP"));
        assertTrue(otps.findById(existing.getId()).isPresent());
        mockMvc.perform(post("/api/v1/register/verify-user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"otp\":\"123456\"}"))
                .andExpect(status().isOk());
        assertTrue(students.findByEmail(EMAIL).orElseThrow().isEnabled());
        assertFalse(otps.findById(existing.getId()).isPresent());
    }

    @Test
    void resetVerificationRejectsDeleteOtp() {
        OTP deletion = seed(OtpType.DELETE, CODE);
        assertThrows(InvalidOtpException.class, () -> registration.verifyForgetPasswordOtp(EMAIL, CODE));
        assertEquals(OtpType.DELETE, otps.findById(deletion.getId()).orElseThrow().getOtpType());
    }

    @Test
    void registrationVerificationRejectsRestoreOtp() {
        student.setEnabled(false);
        students.saveAndFlush(student);
        OTP restore = seed(OtpType.RESTORE, CODE);
        assertThrows(InvalidOtpException.class, () -> registration.verifyUser(EMAIL, CODE));
        assertFalse(students.findById(student.getId()).orElseThrow().isEnabled());
        assertEquals(OtpType.RESTORE, otps.findById(restore.getId()).orElseThrow().getOtpType());
    }

    @Test
    void registrationResendPreservesOtherPurpose() {
        student.setEnabled(false);
        students.saveAndFlush(student);
        OTP deletion = seed(OtpType.DELETE, CODE);
        registration.generateAndSendOtp(EMAIL);
        assertTrue(otps.findById(deletion.getId()).isPresent());
    }

    @Test
    void resetResendPreservesOtherPurpose() {
        OTP deletion = seed(OtpType.DELETE, CODE);
        registration.forgetPassword(EMAIL);
        assertTrue(otps.findById(deletion.getId()).isPresent());
    }

    @Test
    void newResetRequestInvalidatesOldVerifiedResetEvenWhenOtherOtpExists() {
        OTP verifiedReset = seed(OtpType.RESET, CODE);
        OTP deletion = seed(OtpType.DELETE, OTHER_CODE);
        registration.forgetPassword(EMAIL);
        assertTrue(otps.findById(verifiedReset.getId()).isEmpty());
        assertTrue(otps.findById(deletion.getId()).isPresent());
    }

    @Test
    void registrationVerificationConsumesOnlyVerifyOtp() {
        student.setEnabled(false);
        students.saveAndFlush(student);
        OTP verification = seed(OtpType.VERIFY, CODE);
        OTP deletion = seed(OtpType.DELETE, OTHER_CODE);
        registration.verifyUser(EMAIL, CODE);
        assertTrue(students.findById(student.getId()).orElseThrow().isEnabled());
        assertTrue(otps.findById(verification.getId()).isEmpty());
        assertTrue(otps.findById(deletion.getId()).isPresent());
    }

    @Test
    void deleteConfirmationRequiresDeleteOtpAndConsumesIt() {
        authenticate();
        OTP deletion = seed(OtpType.DELETE, CODE);
        OTP other = seed(OtpType.VERIFY, OTHER_CODE);
        assertThrows(globalLogicEx.class, () -> settings.confirmDeleteAccount(OTHER_CODE));
        assertFalse(student.isDeleted());
        assertTrue(otps.findById(other.getId()).isPresent());
        settings.confirmDeleteAccount(CODE);
        assertTrue(students.findById(student.getId()).orElseThrow().isDeleted());
        assertTrue(otps.findById(deletion.getId()).isEmpty());
        assertTrue(otps.findById(other.getId()).isPresent());
    }

    @Test
    void restoreConfirmationRequiresRestoreOtpAndConsumesIt() {
        student.setDeleted(true);
        students.saveAndFlush(student);
        OTP restore = seed(OtpType.RESTORE, CODE);
        OTP other = seed(OtpType.VERIFY, OTHER_CODE);
        assertThrows(globalLogicEx.class, () -> registration.confirmRestoreAccount(EMAIL, OTHER_CODE));
        assertTrue(student.isDeleted());
        assertTrue(otps.findById(other.getId()).isPresent());
        registration.confirmRestoreAccount(EMAIL, CODE);
        assertFalse(students.findById(student.getId()).orElseThrow().isDeleted());
        assertTrue(otps.findById(restore.getId()).isEmpty());
        assertTrue(otps.findById(other.getId()).isPresent());
    }

    @Test
    void changeEmailConfirmationRequiresChangeEmailOtpAndConsumesIt() {
        authenticate();
        OTP change = OTP.builder().user(student).otpType(OtpType.CHANGE_EMAIL).otpCode(CODE)
                .pendingEmail("changed@example.test").expiresAt(LocalDateTime.now().plusMinutes(2)).build();
        change = otps.saveAndFlush(change);
        OTP other = seed(OtpType.VERIFY, OTHER_CODE);
        assertThrows(globalLogicEx.class, () -> settings.confirmChangeEmail(OTHER_CODE));
        assertEquals(EMAIL, student.getEmail());
        assertTrue(otps.findById(other.getId()).isPresent());
        settings.confirmChangeEmail(CODE);
        assertEquals("changed@example.test", students.findById(student.getId()).orElseThrow().getEmail());
        assertTrue(otps.findById(change.getId()).isEmpty());
        assertTrue(otps.findById(other.getId()).isPresent());
    }

    @Test
    void resetRequestAloneCannotChangePassword() {
        registration.forgetPassword(EMAIL);
        assertThrows(globalLogicEx.class, () -> registration.resetPassword(EMAIL, "invalid-token", "new-password"));
        assertEquals("test-password", students.findById(student.getId()).orElseThrow().getPassword());
        assertEquals(1, otps.findAllByUser_EmailAndOtpType(EMAIL, OtpType.RESET_PENDING).size());
    }

    @Test
    void pendingResetCannotAuthorizeFinalResetEvenWithMatchingCode() {
        OTP pending = seed(OtpType.RESET_PENDING, CODE);
        assertThrows(globalLogicEx.class, () -> registration.resetPassword(EMAIL, "invalid-token", "new-password"));
        assertEquals("test-password", student.getPassword());
        assertEquals(OtpType.RESET_PENDING, otps.findById(pending.getId()).orElseThrow().getOtpType());
    }

    @Test
    void resetRequestInvalidatesAllResetStatesAndPreservesOtherTypes() {
        OTP verified = seed(OtpType.RESET, CODE);
        OTP pending = seed(OtpType.RESET_PENDING, OTHER_CODE);
        OTP unrelated = seed(OtpType.CHANGE_EMAIL, "112233");
        registration.forgetPassword(EMAIL);
        assertTrue(otps.findById(verified.getId()).isEmpty());
        assertTrue(otps.findById(pending.getId()).isEmpty());
        assertTrue(otps.findById(unrelated.getId()).isPresent());
        assertThrows(globalLogicEx.class, () -> registration.resetPassword(EMAIL, "invalid-token", "new-password"));
        assertEquals("test-password", student.getPassword());
    }

    @Test
    void validPendingResetTransitionsOnlyAfterVerificationAndCannotBeReused() {
        OTP pending = seed(OtpType.RESET_PENDING, CODE);
        registration.verifyForgetPasswordOtp(EMAIL, CODE);
        assertEquals(OtpType.RESET, otps.findById(pending.getId()).orElseThrow().getOtpType());
        assertThrows(InvalidOtpException.class, () -> registration.verifyForgetPasswordOtp(EMAIL, CODE));
    }

    @Test
    void completeHttpResetRequiresTokenAndConsumesIt() throws Exception {
        mockMvc.perform(post("/forget-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isOk());
        OTP pending = otps.findAllByUser_EmailAndOtpType(EMAIL, OtpType.RESET_PENDING).get(0);
        LocalDateTime beforeVerification = LocalDateTime.now();
        String response = mockMvc.perform(post("/forget-password/verify-otp").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"otp\":\"" + pending.getOtpCode() + "\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        LocalDateTime afterVerification = LocalDateTime.now();
        String token = objectMapper.readTree(response).path("apiResponse").path("resetToken").asText();
        assertEquals(43, token.length());
        OTP verified = otps.findAllByUser_EmailAndOtpType(EMAIL, OtpType.RESET).get(0);
        assertTrue(verified.getOtpCode().matches("v1:[0-9a-f]{64}"));
        assertNotEquals(token, verified.getOtpCode());
        assertFalse(verified.getExpiresAt().isBefore(beforeVerification.plusMinutes(5)));
        assertFalse(verified.getExpiresAt().isAfter(afterVerification.plusMinutes(5)));

        String body = objectMapper.writeValueAsString(java.util.Map.of("email", EMAIL, "resetToken", token,
                "newPassword", "NewPassword123!"));
        mockMvc.perform(post("/forget-password/reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"newPassword\":\"NewPassword123!\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"));
        mockMvc.perform(post("/forget-password/reset").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        assertTrue(passwordEncoder.matches("NewPassword123!", students.findById(student.getId()).orElseThrow().getPassword()));
        assertTrue(otps.findAllByUser_EmailAndOtpType(EMAIL, OtpType.RESET).isEmpty());
        mockMvc.perform(post("/forget-password/reset").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("ERR_BUSINESS_RULE"));
    }

    @Test
    void wrongUserMalformedAndLegacyCredentialsFailClosed() throws Exception {
        seed(OtpType.RESET_PENDING, CODE);
        String token = registration.verifyForgetPasswordOtp(EMAIL, CODE);
        Student other = students.saveAndFlush(Student.builder().firstName("Other").lastName("Student")
                .email("reset-other@example.org").password("other-password").role(student.getRole())
                .educationalLevel(EducationalLevel.FRESH).enabled(true).deleted(false).build());
        otps.saveAndFlush(OTP.builder().user(other).otpType(OtpType.RESET_PENDING).otpCode(OTHER_CODE)
                .expiresAt(LocalDateTime.now().plusMinutes(5)).build());
        registration.verifyForgetPasswordOtp(other.getEmail(), OTHER_CODE);
        String wrongUser = objectMapper.writeValueAsString(java.util.Map.of("email", other.getEmail(),
                "resetToken", token, "newPassword", "NewPassword123!"));
        mockMvc.perform(post("/forget-password/reset").contentType(MediaType.APPLICATION_JSON).content(wrongUser))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("ERR_BUSINESS_RULE"));
        String malformed = objectMapper.writeValueAsString(java.util.Map.of("email", EMAIL,
                "resetToken", "not-a-token", "newPassword", "NewPassword123!"));
        mockMvc.perform(post("/forget-password/reset").contentType(MediaType.APPLICATION_JSON).content(malformed))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("ERR_BUSINESS_RULE"));
        assertEquals("test-password", students.findById(student.getId()).orElseThrow().getPassword());
        assertEquals("other-password", students.findById(other.getId()).orElseThrow().getPassword());
    }

    @Test
    void legacyResetRowCannotAcceptAnyWellFormedToken() throws Exception {
        seed(OtpType.RESET, CODE);
        String wellFormed = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        String body = objectMapper.writeValueAsString(java.util.Map.of("email", EMAIL, "resetToken", wellFormed,
                "newPassword", "NewPassword123!"));
        mockMvc.perform(post("/forget-password/reset").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("ERR_BUSINESS_RULE"));
        assertEquals("test-password", students.findById(student.getId()).orElseThrow().getPassword());
    }

    @Test
    void expiredTokenCannotResetPassword() throws Exception {
        seed(OtpType.RESET_PENDING, CODE);
        String token = registration.verifyForgetPasswordOtp(EMAIL, CODE);
        OTP verified = otps.findAllByUser_EmailAndOtpType(EMAIL, OtpType.RESET).get(0);
        verified.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        otps.saveAndFlush(verified);
        String body = objectMapper.writeValueAsString(java.util.Map.of("email", EMAIL, "resetToken", token,
                "newPassword", "NewPassword123!"));
        mockMvc.perform(post("/forget-password/reset").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isGone()).andExpect(jsonPath("$.errorCode").value("ERR_OTP_EXPIRED"));
        assertEquals("test-password", students.findById(student.getId()).orElseThrow().getPassword());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentResetConsumesCredentialAtMostOnce() throws Exception {
        seed(OtpType.RESET_PENDING, CODE);
        String token = registration.verifyForgetPasswordOtp(EMAIL, CODE);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                start.await();
                try {
                    registration.resetPassword(EMAIL, token, "NewPassword123!");
                    return true;
                } catch (globalLogicEx e) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertTrue(passwordEncoder.matches("NewPassword123!", students.findById(student.getId()).orElseThrow().getPassword()));
            assertTrue(otps.findAllByUser_EmailAndOtpType(EMAIL, OtpType.RESET).isEmpty());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            students.deleteById(student.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void outerTransactionRollbackRestoresPasswordAndCredential() {
        seed(OtpType.RESET_PENDING, CODE);
        String token = registration.verifyForgetPasswordOtp(EMAIL, CODE);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                registration.resetPassword(EMAIL, token, "NewPassword123!");
                entityManager.flush();
                status.setRollbackOnly();
            });
            assertEquals("test-password", students.findById(student.getId()).orElseThrow().getPassword());
            assertEquals(1, otps.findAllByUser_EmailAndOtpType(EMAIL, OtpType.RESET).size());
            registration.resetPassword(EMAIL, token, "NewPassword123!");
            assertTrue(passwordEncoder.matches("NewPassword123!", students.findById(student.getId()).orElseThrow().getPassword()));
        } finally {
            students.deleteById(student.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void consumptionFailureRollsBackPasswordAndKeepsCredentialUsable() {
        seed(OtpType.RESET_PENDING, CODE);
        String token = registration.verifyForgetPasswordOtp(EMAIL, CODE);
        try {
            doThrow(new IllegalStateException("simulated token deletion failure")).when(otps).delete(any(OTP.class));
            assertThrows(IllegalStateException.class,
                    () -> registration.resetPassword(EMAIL, token, "NewPassword123!"));
            assertEquals("test-password", students.findById(student.getId()).orElseThrow().getPassword());
            assertEquals(1, otps.findAllByUser_EmailAndOtpType(EMAIL, OtpType.RESET).size());
            reset(otps);
            registration.resetPassword(EMAIL, token, "NewPassword123!");
            assertTrue(passwordEncoder.matches("NewPassword123!", students.findById(student.getId()).orElseThrow().getPassword()));
            assertTrue(otps.findAllByUser_EmailAndOtpType(EMAIL, OtpType.RESET).isEmpty());
        } finally {
            reset(otps);
            students.deleteById(student.getId());
        }
    }

    @Test
    void pendingResetPersistsAndReloads() {
        OTP pending = seed(OtpType.RESET_PENDING, CODE);
        Long id = pending.getId();
        entityManager.flush();
        entityManager.clear();
        assertEquals(OtpType.RESET_PENDING, otps.findById(id).orElseThrow().getOtpType());
    }

    @Test
    void legacyNullAndDefaultCannotVerifyRegistrationOrReset() {
        student.setEnabled(false);
        students.saveAndFlush(student);
        OTP legacyNull = seed(null, CODE);
        OTP legacyDefault = seed(OtpType.DEFAULT, OTHER_CODE);
        assertThrows(InvalidOtpException.class, () -> registration.verifyUser(EMAIL, CODE));
        assertThrows(InvalidOtpException.class, () -> registration.verifyUser(EMAIL, OTHER_CODE));
        assertThrows(InvalidOtpException.class, () -> registration.verifyForgetPasswordOtp(EMAIL, CODE));
        assertThrows(InvalidOtpException.class, () -> registration.verifyForgetPasswordOtp(EMAIL, OTHER_CODE));
        assertFalse(student.isEnabled());
        assertNull(otps.findById(legacyNull.getId()).orElseThrow().getOtpType());
        assertEquals(OtpType.DEFAULT, otps.findById(legacyDefault.getId()).orElseThrow().getOtpType());
    }

    @Test
    void expiredAndIncorrectResetCodesCannotAdvanceState() {
        OTP pending = seed(OtpType.RESET_PENDING, CODE);
        assertThrows(InvalidOtpException.class, () -> registration.verifyForgetPasswordOtp(EMAIL, OTHER_CODE));
        assertEquals(OtpType.RESET_PENDING, pending.getOtpType());
        pending.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        otps.saveAndFlush(pending);
        assertThrows(OtpExpiredException.class, () -> registration.verifyForgetPasswordOtp(EMAIL, CODE));
        assertTrue(otps.findAllByUser_EmailAndOtpType(EMAIL, OtpType.RESET).isEmpty());
    }

    @Test
    void expiredAndIncorrectRegistrationCodesCannotEnableAccount() {
        student.setEnabled(false);
        students.saveAndFlush(student);
        OTP verification = seed(OtpType.VERIFY, CODE);
        assertThrows(InvalidOtpException.class, () -> registration.verifyUser(EMAIL, OTHER_CODE));
        verification.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        otps.saveAndFlush(verification);
        assertThrows(OtpExpiredException.class, () -> registration.verifyUser(EMAIL, CODE));
        assertFalse(student.isEnabled());
    }

    @Test
    void nullCodeAndMissingExpiryCannotAdvanceOtpFlows() {
        OTP pending = seed(OtpType.RESET_PENDING, CODE);
        assertThrows(InvalidOtpException.class, () -> registration.verifyForgetPasswordOtp(EMAIL, null));
        pending.setExpiresAt(null);
        otps.saveAndFlush(pending);
        assertThrows(OtpExpiredException.class, () -> registration.verifyForgetPasswordOtp(EMAIL, CODE));
        assertTrue(otps.findAllByUser_EmailAndOtpType(EMAIL, OtpType.RESET).isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void accountLockSerializesConcurrentOtpTransactions() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                users.lockIdByEmail(EMAIL).orElseThrow();
                firstLocked.countDown();
                try {
                    if (!releaseFirst.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("lock release timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            assertTrue(firstLocked.await(5, TimeUnit.SECONDS));
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET LOCK_TIMEOUT 200");
                }
                try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM users WHERE email = ? FOR UPDATE")) {
                    statement.setString(1, EMAIL);
                    SQLException conflict = assertThrows(SQLException.class, statement::executeQuery);
                    assertEquals(50200, conflict.getErrorCode(), "H2 must report a row lock timeout");
                } finally {
                    connection.rollback();
                }
            }
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    assertTrue(users.lockIdByEmail(EMAIL).isPresent()));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            students.deleteById(student.getId());
        }
    }

    @Test
    void correctCodeForDifferentAccountDoesNotVerify() {
        OTP pending = seed(OtpType.RESET_PENDING, CODE);
        assertThrows(InvalidOtpException.class, () -> registration.verifyForgetPasswordOtp("other@example.test", CODE));
        assertEquals(OtpType.RESET_PENDING, otps.findById(pending.getId()).orElseThrow().getOtpType());
    }

    @Test
    void duplicatePurposeRowsFailClosedWithoutConsumingOtherPurposes() {
        seed(OtpType.RESET_PENDING, CODE);
        seed(OtpType.RESET_PENDING, CODE);
        OTP other = seed(OtpType.DELETE, OTHER_CODE);
        assertThrows(InvalidOtpException.class, () -> registration.verifyForgetPasswordOtp(EMAIL, CODE));
        assertEquals(2, otps.findAllByUser_EmailAndOtpType(EMAIL, OtpType.RESET_PENDING).size());
        assertTrue(otps.findById(other.getId()).isPresent());
        assertTrue(otps.findAllByUser_EmailAndOtpType(EMAIL, OtpType.RESET).isEmpty());
    }

    @Test
    void duplicateVerifiedResetsCannotAuthorizeFinalReset() {
        seed(OtpType.RESET, CODE);
        seed(OtpType.RESET, OTHER_CODE);
        assertThrows(globalLogicEx.class, () -> registration.resetPassword(EMAIL, "invalid-token", "new-password"));
        assertEquals("test-password", student.getPassword());
    }

    @Test
    void registrationVerificationCannotBeReused() {
        student.setEnabled(false);
        students.saveAndFlush(student);
        OTP verification = seed(OtpType.VERIFY, CODE);
        registration.verifyUser(EMAIL, CODE);
        assertTrue(otps.findById(verification.getId()).isEmpty());
        assertThrows(UserAlreadyVerifiedException.class, () -> registration.verifyUser(EMAIL, CODE));
    }

    @Test
    void forgotPasswordEndpointKeepsSuccessResponse() throws Exception {
        mockMvc.perform(post("/forget-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.message").value("OTP has sent to your email"));
        assertEquals(1, otps.findAllByUser_EmailAndOtpType(EMAIL, OtpType.RESET_PENDING).size());
    }
}
