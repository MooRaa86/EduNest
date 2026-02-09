package com.example.gradproj.EduNest.repository.livesession;

import com.example.gradproj.EduNest.entity.livesession.Session;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveSessionRepository extends JpaRepository<Session,Long> {

}
