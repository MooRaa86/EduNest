package com.example.gradproj.EduNest.repository.projects;

import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.entity.tasks.TaskSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectSubmissionRepository extends JpaRepository<ProjectSubmission,Long> {
    List<ProjectSubmission> findByProject_id (long project_Id);
    Optional<ProjectSubmission> findByProject_IdAndStudent_Id(Long projectId, Long studentId);
    @Query("""
        select coalesce(sum(ps.finalScore), 0)
        from ProjectSubmission  ps
        where ps.student.id = :studentId
          and ps.project.mentorship.id = :mentorshipId
          and ps.status = com.example.gradproj.EduNest.enums.tasks.SubmissionStatus.GRADED
          and ps.finalScore is not null
    """)
    int sumFinalScoresForMentorship(@Param("studentId") Long studentId,
                                    @Param("mentorshipId") Long mentorshipId);
}
