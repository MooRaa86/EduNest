package com.example.gradproj.EduNest.service;

import com.example.gradproj.EduNest.dto.MentorRequestDto;
import com.example.gradproj.EduNest.dto.StudenRequestDto;
import com.example.gradproj.EduNest.entity.Mentor;
import com.example.gradproj.EduNest.entity.Roles;
import com.example.gradproj.EduNest.entity.Student;
import com.example.gradproj.EduNest.repository.MentorRepository;
import com.example.gradproj.EduNest.repository.StudentRepository;
import com.example.gradproj.EduNest.repository.roleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class registerServiceImpl implements RegisterationService{

    private final MentorRepository mentorRepository;
    private final StudentRepository studentRepository;
    private final roleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;


    @Override
    public boolean registerStudent(StudenRequestDto studentDto) {
        if(studentRepository.existsByEmail(studentDto.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        Roles role = roleRepository.findById(studentDto.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found"));

        if(!role.getRoleName().equals("STUDENT")) {
            throw new RuntimeException("Role mismatch");
        }

        Student student = new Student();

        student.setFirstName(studentDto.getFirstName());
        student.setLastName(studentDto.getLastName());
        student.setEmail(studentDto.getEmail());
        student.setPassword(passwordEncoder.encode(studentDto.getPassword()));
        student.setPhoneNumber(studentDto.getPhoneNumber());
        student.setRoles(role);
        student.setEducationalLevel(studentDto.getEducationalLevel());

        Student newStudent = studentRepository.save(student);

        return newStudent.getId() != null;

    }

    @Override
    public boolean registerMentor(MentorRequestDto mentorRequestDto) {
        if (mentorRepository.existsByEmail(mentorRequestDto.getEmail())) {
           throw new RuntimeException("Email already exists");
        }

        Roles role = roleRepository.findById(mentorRequestDto.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found"));

        if(!role.getRoleName().equals("MENTOR")) {
            throw new RuntimeException("Role mismatch");
        }

        Mentor mentor = new Mentor();
        mentor.setFirstName(mentorRequestDto.getFirstName());
        mentor.setLastName(mentorRequestDto.getLastName());
        mentor.setEmail(mentorRequestDto.getEmail());
        mentor.setPassword(passwordEncoder.encode(mentorRequestDto.getPassword()));
        mentor.setPhoneNumber(mentorRequestDto.getPhoneNumber());
        mentor.setBio(mentorRequestDto.getBio());
        mentor.setRoles(role);
        mentor.setGithubUrl(mentorRequestDto.getGithubUrl());
        mentor.setLinkedInUrl(mentorRequestDto.getLinkedInUrl());
        mentor.setJobTitle(mentorRequestDto.getJobTitle());
        mentor.setYearsOfExperience(mentorRequestDto.getYearsOfExperience());

        Mentor newMentor=  mentorRepository.save(mentor);

        return newMentor.getId() != null;

    }
}
