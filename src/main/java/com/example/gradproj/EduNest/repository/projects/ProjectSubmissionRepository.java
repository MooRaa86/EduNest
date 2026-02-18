package com.example.gradproj.EduNest.repository.projects;

import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectSubmissionRepository extends JpaRepository<ProjectSubmission,Long> {
    List<ProjectSubmission> findByProject_id (long project_Id);
    Optional<ProjectSubmission> findByProject_IdAndStudent_Id(Long projectId, Long studentId);
    @Query("""
        SELECT ps
        FROM ProjectSubmission ps
        JOIN FETCH ps.project p
        JOIN FETCH p.week w
        JOIN FETCH w.mentorship m
        WHERE ps.student.id = :studentId
          AND (:status IS NULL OR ps.status = :status)
        ORDER BY ps.submittedAt DESC
    """)
    Page<ProjectSubmission> findForStudentProfile(
            @Param("studentId") Long studentId,
            @Param("status") SubmissionStatus status,
            Pageable pageable
    );
}
