package com.example.gradproj.EduNest.repository.mentorship;

import com.example.gradproj.EduNest.entity.mentorship.Mentorship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface mentorShipRepository extends JpaRepository<Mentorship, Long> {

}