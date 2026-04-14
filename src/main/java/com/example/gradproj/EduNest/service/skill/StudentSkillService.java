package com.example.gradproj.EduNest.service.skill;

import com.example.gradproj.EduNest.dto.skill.response.SkillResponse;
import com.example.gradproj.EduNest.entity.skill.StudentSkill;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.skill.StudentSkillRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentSkillService {

    private final StudentSkillRepository studentSkillRepository;
    private final StudentRepository studentRepository;

    public void addSkill(String skillName){
        Student student=getCurrentStudent();

        skillName=skillName.trim().toLowerCase();

        if (studentSkillRepository.existsByStudentIdAndSkillName(student.getId(), skillName)) {
           throw new globalLogicEx("skill already exist");
        }

        StudentSkill skill=StudentSkill.builder()
                .skillName(skillName)
                .student(student)
                .build();
        studentSkillRepository.save(skill);
    }

    public void deleteSkill(String skillName){
        Student student=getCurrentStudent();
        studentSkillRepository.deleteByStudentIdAndSkillName(student.getId(), skillName.trim().toLowerCase());
    }

    public List<SkillResponse>getAllStudentSkills(){
        Student student=getCurrentStudent();
        return studentSkillRepository.findAllByStudent(student).stream()
                .map(skill-> SkillResponse.builder().skillName(skill.getSkillName()).build())
                .toList();
    }

    private Student getCurrentStudent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated user");
        }
        return studentRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));
    }
}
