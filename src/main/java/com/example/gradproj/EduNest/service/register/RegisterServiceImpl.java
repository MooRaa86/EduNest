package com.example.gradproj.EduNest.service.register;

import com.example.gradproj.EduNest.dto.register.MentorRequestDto;
import com.example.gradproj.EduNest.dto.register.StudentRequestDto;
import com.example.gradproj.EduNest.entity.*;
import com.example.gradproj.EduNest.entity.register.OTP;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.enums.register.OtpType;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.exception.registerExceptions.*;
import com.example.gradproj.EduNest.repository.*;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import static com.example.gradproj.EduNest.utils.SystemUtils.*;

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
    private final TemplateEngine templateEngine;
    private final int expiryTime = 2;

    @Override
    public String generateOTP() {
        SecureRandom random = new SecureRandom();
        return String.valueOf(100000 + random.nextInt(900000));
    }

    private void processAndSendOtp(UserEntity user,String template) {

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

        // Prepare the evaluation context
        String htmlTemplate = emailService.getEmailTemplate(template);


        // Process the template
        String html = htmlTemplate.replace("{{OTP_CODE}}", otpCode)
                .replace("{{USER_NAME}}", user.getFirstName() + " " + user.getLastName())
                .replace("{{EXPIRY_MINUTES}}", String.valueOf(expiryTime));

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

        Roles role = roleRepository.findByName(STUDENT)
                .orElseThrow(() -> new RoleNotFoundException("Error: Role STUDENT not found."));

        Student student = Student.builder()
                .firstName(studentDto.getFirstName())
                .lastName(studentDto.getLastName())
                .email(studentDto.getEmail())
                .password(passwordEncoder.encode(studentDto.getPassword()))
                .phoneNumber(studentDto.getPhoneNumber())
                .role(role)
                .educationalLevel(studentDto.getEducationalLevel())
                .enabled(false)
                .build();

        studentRepository.save(student);

        String template = "otp-template.html";
        processAndSendOtp(student, template);

    }

    @Override
    @Transactional
    public void registerMentor(MentorRequestDto mentorRequestDto) {

        if (userRepository.existsByEmail(mentorRequestDto.getEmail())) {
            throw new EmailAlreadyExistsException("Email already exists");
        }

        Roles role = roleRepository.findByName(MENTOR)
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
//                .createdBy(SYSTEM)
//                .updatedBy(SYSTEM)
//                after login
//        student.setUpdatedBy(currentUser.getUsername());
//        student.setUpdatedAt(LocalDateTime.now());

                .enabled(false)
                .build();

        mentorRepository.save(mentor);

        String template = "otp-template.html";
        processAndSendOtp(mentor, template);

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
        user.setUpdatedBy(SYSTEM);
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
        String template = "otp-template.html";
        processAndSendOtp(user,template);
    }

    @Override
    public void forgetPassword(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Email not found"));

        if (!user.isEnabled()) {
            throw new globalLogicEx("User is not verified yet");
        }

        String template = "forget-password-otp-template.html";
        processAndSendOtp(user, template);
    }

    @Override
    @Transactional
    public void verifyForgetPasswordOtp(String email, String otpCode) {

        OTP otpEntity = otpRepository
                .findByUser_EmailAndOtpCode(email, otpCode)
                .orElseThrow(() -> new InvalidOtpException("Invalid OTP"));

        if (otpEntity.getExpiresAt().isBefore(LocalDateTime.now())) {
            otpRepository.delete(otpEntity);
            throw new OtpExpiredException("Expired OTP, please request a new one.");
        }

        otpEntity.setOtpType(OtpType.RESET);
        otpRepository.save(otpEntity);
        //  متحذفش OTP هنا
        // بس اتأكد إنه صح
    }

    @Override
    @Transactional
    public void resetPassword(String email, String newPassword) {

        OTP otp = otpRepository.findByUser_Email(email)
                .orElseThrow(() ->
                        new globalLogicEx("OTP verification required"));

        if (!OtpType.RESET.equals(otp.getOtpType())) {
            otpRepository.delete(otp);
            throw new globalLogicEx("Invalid OTP, please request a new one.");
        }

        if(otp.getCreatedAt().plusHours(1).isBefore(LocalDateTime.now())) {
            otpRepository.delete(otp);
            throw new OtpExpiredException("Expired OTP, This process failed you have to request a new verify otp");
        }

        UserEntity user = otp.getUser();

        if (!user.isEnabled()){
            throw new globalLogicEx("User is not verified yet");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        otpRepository.delete(otp);

    }

}