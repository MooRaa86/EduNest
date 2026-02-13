package com.example.gradproj.EduNest.repository.week;

import com.example.gradproj.EduNest.entity.mentorship.Week;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeekRepository extends JpaRepository<Week,Long> {
    void deleteById(Long id);
    List<Week> findByMentorship_IdOrderByIdAsc(Long mentorshipId);

}
