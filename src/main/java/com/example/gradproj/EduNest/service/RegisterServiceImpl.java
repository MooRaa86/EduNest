package com.example.gradproj.EduNest.service;

import com.example.gradproj.EduNest.dto.MentorRequestDto;
import com.example.gradproj.EduNest.dto.StudentRequestDto;
import com.example.gradproj.EduNest.entity.Mentor;
import com.example.gradproj.EduNest.entity.Roles;
import com.example.gradproj.EduNest.entity.Student;
import com.example.gradproj.EduNest.repository.MentorRepository;
import com.example.gradproj.EduNest.repository.RoleRepository;
import com.example.gradproj.EduNest.repository.StudentRepository;
import com.example.gradproj.EduNest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterServiceImpl implements RegistrationService {

    private final UserRepository userRepository;
    private final MentorRepository mentorRepository;
    private final StudentRepository studentRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;


    @Override
    @Transactional
    public void registerStudent(StudentRequestDto studentDto) {

        if (userRepository.existsByEmail(studentDto.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        Roles role = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new RuntimeException("Error: Role STUDENT not found."));

        Student student = Student.builder()
                .firstName(studentDto.getFirstName())
                .lastName(studentDto.getLastName())
                .email(studentDto.getEmail())
                .password(passwordEncoder.encode(studentDto.getPassword()))
                .phoneNumber(studentDto.getPhoneNumber())
                .role(role)
                .educationalLevel(studentDto.getEducationalLevel())
                .build();

        Student newStudent = studentRepository.save(student);

        if (newStudent.getId() < 1) {
            throw new RuntimeException("Error: Student not created.");
        }

    }

    @Override
    @Transactional
    public void registerMentor(MentorRequestDto mentorRequestDto) {

        if (userRepository.existsByEmail(mentorRequestDto.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        Roles role = roleRepository.findByName("MENTOR")
                .orElseThrow(() -> new RuntimeException("Error: Role MENTOR not found."));

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

        Mentor newMentor = mentorRepository.save(mentor);

        if (newMentor.getId() < 1) {
            throw new RuntimeException("Error: Mentor not created.");
        }

    }
}
