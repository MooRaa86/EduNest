package com.example.gradproj.EduNest.repository.projects;

import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.enums.project.ProjectStatus;
import com.example.gradproj.EduNest.repository.projects.projection.ProjectDashboardProjection;
import com.example.gradproj.EduNest.repository.projects.projection.ProjectWithStatsProjection;
import com.example.gradproj.EduNest.repository.projects.projection.UpcomingProjectProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project,Long> {
    boolean existsById(Long id);
    List<Project> findByWeek_Mentorship_Id(Long mentorshipId);

    @Query("""
    SELECT
        COUNT(p)                                                   AS totalProjects,
        SUM(CASE WHEN p.status = 'PUBLISHED' THEN 1 ELSE 0 END)   AS publishedCount,
        SUM(CASE WHEN p.status = 'DRAFT'     THEN 1 ELSE 0 END)   AS draftCount,
        AVG(COALESCE(s.finalScore, 0))                             AS averageScore
    FROM Project p
    LEFT JOIN p.submissions s
    WHERE p.week.mentorship.id = :mentorshipId
    """)
    ProjectDashboardProjection getDashboardStats(@Param("mentorshipId") Long mentorshipId);

    @Query("""
    SELECT
        p.id                          AS id,
        p.title                       AS title,
        p.goal                        AS goal,
        p.brief                       AS brief,
        p.descriptionUrl              AS descriptionUrl,
        p.uploadedAttachmentPath      AS uploadedAttachmentPath,
        p.startAt                     AS startAt,
        p.endAt                       AS endAt,
        p.points                      AS points,
        p.status                      AS status,
        p.week.id                     AS weekId,
        p.createdAt                   AS createdAt,
        COUNT(DISTINCT e.id)          AS totalStudents,
        COUNT(DISTINCT ps.id)         AS submissionsCount
    FROM Project p
    JOIN p.week w
    JOIN w.mentorship m
    LEFT JOIN Enrollment e  ON e.mentorShip.id = m.id
    LEFT JOIN ProjectSubmission ps ON ps.project.id = p.id
    WHERE p.week.mentorship.id = :msid
      AND (:projectName IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :projectName, '%')))
      AND (:status IS NULL OR p.status = :status)
    GROUP BY p.id, p.title, p.goal, p.brief, p.descriptionUrl, p.uploadedAttachmentPath,
             p.startAt, p.endAt, p.points, p.status, p.week.id, p.createdAt
    """)
    Page<ProjectWithStatsProjection> findProjectsWithStatsByMentorship(
            @Param("msid") Long msid,
            @Param("projectName") String projectName,
            @Param("status") ProjectStatus status,
            Pageable pageable
    );

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


    @Query("""
    SELECT p.id as id, p.title as title, p.endAt as endAt, p.points as points,
           w.id as weekId, w.title as weekTitle,
           m.id as mentorshipId, m.title as mentorshipTitle
    FROM Project p
    JOIN p.week w
    JOIN w.mentorship m
    WHERE p.status = 'PUBLISHED'
      AND p.endAt > :now
      AND EXISTS (
        SELECT 1 FROM Enrollment e
        WHERE e.mentorShip.id = m.id
          AND e.student.email = :email
      )
      AND NOT EXISTS (
        SELECT 1 FROM ProjectSubmission ps
        WHERE ps.project.id = p.id
          AND ps.student.email = :email
      )
    ORDER BY p.endAt ASC
""")
    List<UpcomingProjectProjection> findUpcomingProjectsByStudentEmail(
            @Param("email") String email,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
