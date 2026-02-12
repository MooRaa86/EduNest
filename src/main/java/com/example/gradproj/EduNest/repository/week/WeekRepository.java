package com.example.gradproj.EduNest.repository.week;

import com.example.gradproj.EduNest.entity.weeks.MentorShipWeek;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeekRepository extends JpaRepository<MentorShipWeek,Long> {
    void deleteById(Long id);
    List<MentorShipWeek> findByMentorship_IdOrderByIdAsc(Long mentorshipId);

}
