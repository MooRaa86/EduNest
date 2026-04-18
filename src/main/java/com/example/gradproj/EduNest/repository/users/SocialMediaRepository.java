package com.example.gradproj.EduNest.repository.users;

import com.example.gradproj.EduNest.entity.users.SocialMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SocialMediaRepository extends JpaRepository<SocialMedia, Long> {
    List<SocialMedia> findByUserEmail(String email);
}
