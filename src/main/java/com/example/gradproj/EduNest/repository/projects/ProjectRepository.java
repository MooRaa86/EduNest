package com.example.gradproj.EduNest.repository.projects;

import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project,Long> {
    boolean existsById(Long id);
    List<Project> findByWeek_Mentorship_Id(Long mentorshipId);

    @Query("""
    SELECT p FROM Project p
    WHERE p.week.mentorship.id = :msid
      AND (:projectName IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :projectName, '%')))
      AND (:status IS NULL OR p.status = :status)
""")
    Page<Project> findProjectsByMentorship(
            @Param("msid") Long msid,
            @Param("projectName") String projectName,
            @Param("status") ProjectStatus status,
            Pageable pageable
    );
    List<Project> findByWeek_Id(Long weekId);
    List<Project> findByWeek_IdAndStatusNot(Long weekId, ProjectStatus status);
    void deleteById(Long id);
}
