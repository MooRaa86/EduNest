package com.example.gradproj.EduNest.repository.projects;

import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProjectSubmissionRepository extends JpaRepository<ProjectSubmission,Long> {
    Page<ProjectSubmission> findByProject_Id(Long projectId, Pageable pageable);
    Optional<ProjectSubmission> findByProject_IdAndStudent_Id(Long projectId, Long studentId);
    @Query(
            value = """
    SELECT ps
    FROM ProjectSubmission ps
    JOIN FETCH ps.project p
    JOIN FETCH p.week w
    JOIN FETCH w.mentorship m
    WHERE ps.student.id = :studentId
      AND (:status IS NULL OR ps.status = :status)
    ORDER BY ps.submittedAt DESC
  """,
            countQuery = """
    SELECT COUNT(ps)
    FROM ProjectSubmission ps
    JOIN ps.project p
    JOIN p.week w
    JOIN w.mentorship m
    WHERE ps.student.id = :studentId
      AND (:status IS NULL OR ps.status = :status)
  """
    )
    Page<ProjectSubmission> findForStudentProfile(
            Long studentId,
            SubmissionStatus status,
            Pageable pageable
    );


}
