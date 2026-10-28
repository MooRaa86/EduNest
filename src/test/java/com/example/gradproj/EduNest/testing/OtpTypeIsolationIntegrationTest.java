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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OtpTypeIsolationIntegrationTest extends TransactionalIntegrationTestBase {
    private static final String EMAIL = "otp-isolation@example.org";
    private static final String CODE = "123456";
    private static final String OTHER_CODE = "654321";

    @Autowired private RegisterServiceImpl registration;
    @Autowired private SettingsService settings;
    @Autowired private OTPRepository otps;
    @Autowired private StudentRepository students;
    @Autowired private RoleRepository roles;
    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    @MockBean private EmailService emailService;

    private Student student;

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
        assertThrows(globalLogicEx.class, () -> registration.resetPassword(EMAIL, "new-password"));
        assertEquals("test-password", students.findById(student.getId()).orElseThrow().getPassword());
        assertEquals(1, otps.findAllByUser_EmailAndOtpType(EMAIL, OtpType.RESET_PENDING).size());
    }

    @Test
    void pendingResetCannotAuthorizeFinalResetEvenWithMatchingCode() {
        OTP pending = seed(OtpType.RESET_PENDING, CODE);
        assertThrows(globalLogicEx.class, () -> registration.resetPassword(EMAIL, "new-password"));
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
        assertThrows(globalLogicEx.class, () -> registration.resetPassword(EMAIL, "new-password"));
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
        assertThrows(globalLogicEx.class, () -> registration.resetPassword(EMAIL, "new-password"));
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
