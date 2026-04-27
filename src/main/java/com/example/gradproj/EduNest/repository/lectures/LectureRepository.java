package com.example.gradproj.EduNest.repository.lectures;

import com.example.gradproj.EduNest.entity.lectures.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LectureRepository extends JpaRepository<Lecture, Long> {
    List<Lecture> findByWeek_Id(Long weekId);

    List<Lecture> findByWeek_IdIn(List<Long> weekIds);
}
