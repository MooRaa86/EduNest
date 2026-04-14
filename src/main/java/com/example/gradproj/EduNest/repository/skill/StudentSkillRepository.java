package com.example.gradproj.EduNest.repository.skill;

import com.example.gradproj.EduNest.entity.skill.StudentSkill;
import com.example.gradproj.EduNest.entity.users.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentSkillRepository extends JpaRepository<StudentSkill,Long> {

    boolean existsByStudentIdAndSkillName(Long studentId, String skillName);

    void deleteByStudentIdAndSkillName(Long id, String skillName);

    List<StudentSkill> findAllByStudent(Student student);
}
