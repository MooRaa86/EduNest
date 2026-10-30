package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.dto.register.StudentRequestDto;
import com.example.gradproj.EduNest.service.register.RegistrationService;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthRequestValidationTest extends ProdChainIntegrationTestBase {
    @MockBean private RegistrationService registration;
    @Autowired private AuthFixture fixture;
    @Autowired private Validator validator;
    @Autowired private StudentRepository students;
    @Autowired private PasswordEncoder encoder;

    @Test
    void loginRejectsMalformedInputsWithoutEchoingSecrets() throws Exception {
        for (String body : new String[]{"{}", "{\"email\":null,\"password\":null}",
                "{\"email\":\" \",\"password\":\" \"}"}) {
            mockMvc.perform(post("/login-api").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"))
                    .andExpect(jsonPath("$.fields.email").exists()).andExpect(jsonPath("$.fields.password").exists());
        }
        String response = mockMvc.perform(post("/login-api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid\",\"password\":\"sensitive-secret\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"))
                .andExpect(jsonPath("$.fields.email").exists()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("sensitive-secret", "invalid");
    }

    @Test
    void shortPasswordKeepsAuthenticationSemanticsAndSuccessStillWorks() throws Exception {
        fixture.student("eb32-user@example.org");
        for (String email : new String[]{"eb32-user@example.org", "eb32-unknown@example.org"}) {
            mockMvc.perform(post("/login-api").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"x\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("ERR_INVALID_CREDENTIALS"));
        }
        mockMvc.perform(post("/login-api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"eb32-user@example.org\",\"password\":\"probe-password\"}"))
                .andExpect(status().isOk()).andExpect(header().exists("Authorization"));
        var legacy = fixture.student("eb32-legacy@example.org");
        legacy.setPassword(encoder.encode("short"));
        students.saveAndFlush(legacy);
        mockMvc.perform(post("/login-api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"eb32-legacy@example.org\",\"password\":\"short\"}"))
                .andExpect(status().isOk()).andExpect(header().exists("Authorization"));
    }

    @Test
    void verifyRejectsInvalidInputsBeforeService() throws Exception {
        for (String body : new String[]{"{}", "{\"email\":null,\"otp\":null}",
                "{\"email\":\" \",\"otp\":\" \"}", "{\"email\":\"bad\",\"otp\":\"12a456\"}",
                "{\"email\":\"valid@example.org\",\"otp\":\"123\"}"}) {
            mockMvc.perform(post("/api/v1/register/verify-user").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"))
                    .andExpect(jsonPath("$.fields").isMap());
        }
        verifyNoInteractions(registration);
        mockMvc.perform(post("/api/v1/register/verify-user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"valid@example.org\",\"otp\":\"123456\"}"))
                .andExpect(status().isOk());
        verify(registration).verifyUser("valid@example.org", "123456");
    }

    @Test
    void resendKeepsParameterContractAndRejectsInvalidBeforeService() throws Exception {
        for (String value : new String[]{"", " ", "not-an-email"}) {
            mockMvc.perform(post("/api/v1/register/send-otp").param("email", value))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"))
                    .andExpect(jsonPath("$.fields.email").exists());
        }
        mockMvc.perform(post("/api/v1/register/send-otp"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("ERR_VALIDATION"))
                .andExpect(jsonPath("$.fields.email").exists());
        verifyNoInteractions(registration);
        mockMvc.perform(post("/api/v1/register/send-otp").param("email", "valid@example.org"))
                .andExpect(status().isOk());
        verify(registration).generateAndSendOtp("valid@example.org");
    }

    @Test
    void malformedAndUnknownJsonRetainExistingParseContract() throws Exception {
        for (String body : new String[]{"{", "{\"unexpected\":true}"}) {
            mockMvc.perform(post("/login-api").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("ERR_MALFORMED_BODY"));
            mockMvc.perform(post("/api/v1/register/verify-user").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("ERR_MALFORMED_BODY"));
        }
        verifyNoInteractions(registration);
    }

    @Test
    void registrationPasswordMatchStillUsesModernDto() {
        StudentRequestDto dto = new StudentRequestDto();
        dto.setPassword("password-one");
        dto.setConfirmPassword("password-two");
        assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("confirmPassword")
                && v.getMessage().equals("Passwords do not match"));
        dto.setConfirmPassword("password-one");
        assertThat(validator.validate(dto)).noneMatch(v -> v.getMessage().equals("Passwords do not match"));
    }
}
