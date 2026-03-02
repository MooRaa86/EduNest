package com.example.gradproj.EduNest.service.register;

import com.example.gradproj.EduNest.dto.register.MentorRequestDto;
import com.example.gradproj.EduNest.dto.register.StudentRequestDto;

public interface RegistrationService {
    void registerStudent(StudentRequestDto studentDto);
    void registerMentor(MentorRequestDto mentorRequestDto);
    void verifyUser(String email, String otpCode);
    void generateAndSendOtp(String email);
    String generateOTP();
    void forgetPassword(String email);
    void verifyForgetPasswordOtp(String email, String otpCode);
    void resetPassword(String email, String newPassword);
    void restoreAccount(String email);
    void confirmRestoreAccount(String email, String otpCode);
}
