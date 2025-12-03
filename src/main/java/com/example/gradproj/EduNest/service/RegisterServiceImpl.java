package com.example.gradproj.EduNest.service;

import com.example.gradproj.EduNest.dto.MentorRequestDto;
import com.example.gradproj.EduNest.dto.StudentRequestDto;
import com.example.gradproj.EduNest.entity.*;
import com.example.gradproj.EduNest.exception.*;
import com.example.gradproj.EduNest.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class RegisterServiceImpl implements RegistrationService {

    private final UserRepository userRepository;
    private final MentorRepository mentorRepository;
    private final StudentRepository studentRepository;
    private final RoleRepository roleRepository;
    private final OTPRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final int expiryTime = 2;

    @Override
    public String generateOTP() {
        SecureRandom random = new SecureRandom();
        return String.valueOf(100000 + random.nextInt(900000));
    }

    private void processAndSendOtp(UserEntity user) {

        otpRepository.findByUser_Email(user.getEmail()).ifPresent(existingOtp -> {
            otpRepository.delete(existingOtp);
            otpRepository.flush();
        });

        String otpCode = generateOTP();
        OTP otp = OTP.builder()
                .otpCode(otpCode)
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(expiryTime))
                .build();
        otpRepository.save(otp);

        String html = String.format(
                "<div style=\"font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; max-width: 600px; margin: 0 auto; background-color: #ffffff; border: 1px solid #e0e0e0; position: relative; overflow: hidden;\">" +

                        "  " +
                        "  <div style=\"padding: 40px 40px 20px 40px;\">" +
                        "    <h1 style=\"color: #000000; font-size: 28px; margin: 0; font-weight: bold; letter-spacing: -1px;\">EduNest</h1>" +
                        "  </div>" +

                        "  " +
                        "  <div style=\"padding: 0 40px 100px 40px; color: #333333; line-height: 1.6;\">" +
                        "    <p style=\"font-size: 16px; margin-bottom: 30px;\">Subject: <strong>Verification Code</strong></p>" +
                        "    <p style=\"font-size: 16px; margin-bottom: 20px;\">Dear User,</p>" +
                        "    <p style=\"font-size: 16px; margin-bottom: 30px;\">You are registering with EduNest. To complete your sign-up process, please use the verification code below.</p>" +

                        "    " +
                        "    <div style=\"margin-bottom: 30px;\">" +
                        "      <p style=\"font-size: 14px; color: #666; margin-bottom: 5px;\">Your OTP Code:</p>" +
                        "      <span style=\"font-size: 36px; font-weight: 800; color: #000000; letter-spacing: 2px;\">%s</span>" +
                        "      <p style=\"font-size: 13px; color: #888; margin-top: 10px;\">Valid for <strong>%d minutes</strong> only.</p>" +
                        "    </div>" +

                        "    <p style=\"font-size: 16px; margin-top: 40px;\">Best regards,<br><strong>EduNest Team</strong></p>" +
                        "  </div>" +

                        "</div>",
                otpCode, expiryTime
        );

        emailService.sendEmail(
                user.getEmail(),
                "EduNest Verification Code",
                html
        );
    }

    @Override
    @Transactional
    public void registerStudent(StudentRequestDto studentDto) {

        if (userRepository.existsByEmail(studentDto.getEmail())) {
            throw new EmailAlreadyExistsException("Email already exists");
        }

        Roles role = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new RoleNotFoundException("Error: Role STUDENT not found."));

        Student student = Student.builder()
                .firstName(studentDto.getFirstName())
                .lastName(studentDto.getLastName())
                .email(studentDto.getEmail())
                .password(passwordEncoder.encode(studentDto.getPassword()))
                .phoneNumber(studentDto.getPhoneNumber())
                .role(role)
                .educationalLevel(studentDto.getEducationalLevel())
                .build();

        studentRepository.save(student);

        processAndSendOtp(student);

    }

    @Override
    @Transactional
    public void registerMentor(MentorRequestDto mentorRequestDto) {

        if (userRepository.existsByEmail(mentorRequestDto.getEmail())) {
            throw new EmailAlreadyExistsException("Email already exists");
        }

        Roles role = roleRepository.findByName("MENTOR")
                .orElseThrow(() -> new RoleNotFoundException("Error: Role MENTOR not found."));

        Mentor mentor = Mentor.builder()
                .firstName(mentorRequestDto.getFirstName())
                .lastName(mentorRequestDto.getLastName())
                .email(mentorRequestDto.getEmail())
                .password(passwordEncoder.encode(mentorRequestDto.getPassword()))
                .phoneNumber(mentorRequestDto.getPhoneNumber())
                .role(role)
                .jobTitle(mentorRequestDto.getJobTitle())
                .bio(mentorRequestDto.getBio())
                .linkedInUrl(mentorRequestDto.getLinkedInUrl())
                .githubUrl(mentorRequestDto.getGithubUrl())
                .yearsOfExperience(mentorRequestDto.getYearsOfExperience())
                .build();

        mentorRepository.save(mentor);

        processAndSendOtp(mentor);

    }

    @Override
    public void verifyUser(String email, String otpCode) {

        if(userRepository.existsByEmailAndEnabledTrue(email)) {
            throw new UserAlreadyVerifiedException("Email already verified");
        } // دي مسئوله ان اشوف اليوزر متفعل اصلا ولا لا

        OTP otpEntity = otpRepository.findByUser_EmailAndOtpCode(email, otpCode) // دي بتهندل لو اليوزر متبعتلوش otp او ال otp بتاعه خلص
                .orElseThrow(() -> new InvalidOtpException("This Email doesn't exist or Has no activated OTP request new one."));

        if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            otpRepository.delete(otpEntity);
            throw new OtpExpiredException("OTP Expired, please request a new one.");
        } // هنا بنشوف لو ال otp expired بنحذفه

        UserEntity user = otpEntity.getUser();
        user.setEnabled(true);
        userRepository.save(user);

        otpRepository.delete(otpEntity);
    }

    @Transactional
    public void generateAndSendOtp(String email) {

        if(userRepository.existsByEmailAndEnabledTrue(email)) {
            throw new UserAlreadyVerifiedException("Email already verified no need to send otp");
        }

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        processAndSendOtp(user);
    }

}
