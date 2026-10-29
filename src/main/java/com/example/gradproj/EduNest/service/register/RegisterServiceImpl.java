package com.example.gradproj.EduNest.service.register;

import com.example.gradproj.EduNest.dto.register.MentorRequestDto;
import com.example.gradproj.EduNest.dto.register.StudentRequestDto;
import com.example.gradproj.EduNest.entity.Roles;
import com.example.gradproj.EduNest.entity.register.OTP;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.SocialMedia;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.enums.register.OtpType;
import com.example.gradproj.EduNest.enums.socialMedia.Media;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.exception.registerExceptions.*;
import com.example.gradproj.EduNest.repository.OTPRepository;
import com.example.gradproj.EduNest.repository.RoleRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private final ResetTokenService resetTokenService;
    private final int expiryTime = 2;

    @Override
    public String generateOTP() {
        SecureRandom random = new SecureRandom();
        return String.valueOf(100000 + random.nextInt(900000));
    }

    private void processAndSendOtp(UserEntity user, String template, OtpType type) {
        otpRepository.deleteByUserAndOtpType(user, type);
        if (type == OtpType.RESET_PENDING) {
            otpRepository.deleteByUserAndOtpType(user, OtpType.RESET);
        }
        otpRepository.flush();

        String otpCode = generateOTP();
        OTP otp = OTP.builder()
                .otpCode(otpCode)
                .user(user)
                .otpType(type)
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
        processAndSendOtp(student, template, OtpType.VERIFY);

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
                .yearsOfExperience(mentorRequestDto.getYearsOfExperience())
                .enabled(false)
                .socialMediaLinks(new ArrayList<>())
                .build();

        if (mentorRequestDto.getGithubUrl() != null) {
            SocialMedia github = SocialMedia.builder()
                    .name(Media.GITHUB)
                    .url(mentorRequestDto.getGithubUrl())
                    .user(mentor)
                    .build();
            mentor.getSocialMediaLinks().add(github);
        }
        if (mentorRequestDto.getLinkedInUrl() != null) {
            SocialMedia linkedin = SocialMedia.builder()
                    .name(Media.LINKEDIN)
                    .url(mentorRequestDto.getLinkedInUrl())
                    .user(mentor)
                    .build();
            mentor.getSocialMediaLinks().add(linkedin);
        }

        mentorRepository.save(mentor);

        String template = "otp-template.html";
        processAndSendOtp(mentor, template, OtpType.VERIFY);

    }

    @Override
    @Transactional
    public void verifyUser(String email, String otpCode) {

        userRepository.lockIdByEmail(email).orElseThrow(() ->
                new InvalidOtpException("This Email doesn't exist or Has no activated OTP request new one."));

        if(userRepository.existsByEmailAndEnabledTrue(email)) {
            throw new UserAlreadyVerifiedException("Email already verified");
        } // دي مسئوله ان اشوف اليوزر متفعل اصلا ولا لا

        OTP otpEntity = findSingleOtp(email, OtpType.VERIFY, "This Email doesn't exist or Has no activated OTP request new one.");
        if (otpCode == null || !otpCode.equals(otpEntity.getOtpCode())) {
            throw new InvalidOtpException("This Email doesn't exist or Has no activated OTP request new one.");
        }

        if (otpEntity.getExpiresAt() == null || !otpEntity.getExpiresAt().isAfter(LocalDateTime.now())) {
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

        userRepository.lockIdByEmail(email).orElseThrow(() -> new UserNotFoundException("User not found"));

        if(userRepository.existsByEmailAndEnabledTrue(email)) {
            throw new UserAlreadyVerifiedException("Email already verified no need to send otp");
        }

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        String template = "otp-template.html";
        processAndSendOtp(user, template, OtpType.VERIFY);
    }

    @Override
    @Transactional
    public void forgetPassword(String email) {
        userRepository.lockIdByEmail(email).orElseThrow(() -> new UserNotFoundException("Email not found"));
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Email not found"));

        if (!user.isEnabled()) {
            throw new globalLogicEx("User is not verified yet");
        }

        String template = "forget-password-otp-template.html";
        processAndSendOtp(user, template, OtpType.RESET_PENDING);
    }

    @Override
    @Transactional
    public String verifyForgetPasswordOtp(String email, String otpCode) {

        userRepository.lockIdByEmail(email).orElseThrow(() -> new InvalidOtpException("Invalid OTP"));

        OTP otpEntity = findSingleOtp(email, OtpType.RESET_PENDING, "Invalid OTP");
        if (otpCode == null || !otpCode.equals(otpEntity.getOtpCode())) {
            throw new InvalidOtpException("Invalid OTP");
        }

        if (otpEntity.getExpiresAt() == null || !otpEntity.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new OtpExpiredException("Expired OTP, please request a new one.");
        }

        String token = resetTokenService.generate();
        otpEntity.setOtpCode(resetTokenService.digest(token));
        otpEntity.setExpiresAt(LocalDateTime.now().plus(resetTokenService.lifetime()));
        otpEntity.setOtpType(OtpType.RESET);
        otpRepository.save(otpEntity);
        return token;
    }

    @Override
    @Transactional
    public void resetPassword(String email, String resetToken, String newPassword) {

        userRepository.lockIdByEmail(email).orElseThrow(() -> new globalLogicEx("OTP verification required"));

        List<OTP> verifiedResets = otpRepository.findAllByUser_EmailAndOtpType(email, OtpType.RESET);
        if (verifiedResets.size() != 1) {
            throw new globalLogicEx("OTP verification required");
        }
        OTP otp = verifiedResets.get(0);

        if (!resetTokenService.matches(resetToken, otp.getOtpCode())) {
            throw new globalLogicEx("Invalid or expired reset credential");
        }
        if (otp.getExpiresAt() == null || !otp.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new OtpExpiredException("Reset credential has expired. Please request a new one.");
        }

        UserEntity user = otp.getUser();

        if (!user.isEnabled()){
            throw new globalLogicEx("User is not verified yet");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        otpRepository.delete(otp);

    }

    private OTP findSingleOtp(String email, OtpType type, String errorMessage) {
        List<OTP> matching = otpRepository.findAllByUser_EmailAndOtpType(email, type);
        if (matching.size() != 1) {
            throw new InvalidOtpException(errorMessage);
        }
        return matching.get(0);
    }

    @Override
    @Transactional
    public void restoreAccount(String email){
        UserEntity user = userRepository.findByEmail(email).orElseThrow(
                ()-> new UsernameNotFoundException("User not found"));

        if(!user.isDeleted()){
            throw new globalLogicEx("Account isn't deleted try to login");
        }

        otpRepository.deleteByUserAndOtpType(user, OtpType.RESTORE);
        otpRepository.flush();

        String otpCode = generateOTP();

        OTP otp = OTP.builder()
                .otpCode(otpCode)
                .user(user)
                .otpType(OtpType.RESTORE)
                .expiresAt(LocalDateTime.now().plusMinutes(expiryTime))
                .build();

        otpRepository.save(otp);

        String template = emailService.getEmailTemplate("restore-account.html");

        String html = template
                .replace("{{USER_NAME}}", user.getFirstName())
                .replace("{{OTP_CODE}}", otpCode)
                .replace("{{EXPIRY_MINUTES}}", String.valueOf(expiryTime));

        emailService.sendEmail(
                user.getEmail(),
                "Restore Your EduNest Account",
                html
        );
    }

    @Override
    @Transactional
    public void confirmRestoreAccount(String email, String otpCode) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        OTP otp = otpRepository
                .findByUserAndOtpCodeAndOtpType(user, otpCode, OtpType.RESTORE)
                .orElseThrow(() -> new globalLogicEx("Invalid OTP"));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            otpRepository.delete(otp);
            throw new globalLogicEx("OTP expired");
        }

        otpRepository.deleteByUserAndOtpType(user, OtpType.RESTORE);
        otpRepository.flush();

        user.setDeleted(false);
        userRepository.save(user);
    }


}
