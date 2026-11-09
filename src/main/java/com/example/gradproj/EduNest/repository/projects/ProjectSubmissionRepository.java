package com.example.gradproj.EduNest.repository.projects;

import java.util.UUID;

import com.example.gradproj.EduNest.entity.projects.ProjectSubmission;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.repository.projects.projection.ProjectSubmissionAuthProjection;
import com.example.gradproj.EduNest.repository.projects.projection.ProjectWithSubmissionProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectSubmissionRepository extends JpaRepository<ProjectSubmission,Long> {
    @Query("SELECT e FROM ProjectSubmission e WHERE e.publicId = :publicId")
    java.util.Optional<ProjectSubmission> findByPublicIdDirect(@Param("publicId") UUID publicId);

    default java.util.Optional<ProjectSubmission> findByPublicId(UUID publicId) {
        if (publicId == null) return java.util.Optional.empty();
        java.util.Optional<ProjectSubmission> entity = findByPublicIdDirect(publicId);
        if (entity.isPresent()) return entity;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits());
        }
        return java.util.Optional.empty();
    }
    @Query("SELECT ps.id FROM ProjectSubmission ps WHERE ps.publicId = :publicId")
    java.util.Optional<Long> findIdByPublicIdDirect(@Param("publicId") UUID publicId);

    default java.util.Optional<Long> findIdByPublicId(UUID publicId) {
        if (publicId == null) return java.util.Optional.empty();
        java.util.Optional<Long> id = findIdByPublicIdDirect(publicId);
        if (id.isPresent()) return id;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits()).map(ProjectSubmission::getId);
        }
        return java.util.Optional.empty();
    }
    boolean existsByPublicId(java.util.UUID publicId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ps FROM ProjectSubmission ps WHERE ps.publicId = :publicId")
    java.util.Optional<ProjectSubmission> findForUpdateByPublicId(@Param("publicId") java.util.UUID publicId);

    @Query("""
        SELECT ps.id AS id,
               s.email AS studentEmail,
               m.email AS mentorEmail,
               ps.uploadedFilePath AS filePath
        FROM ProjectSubmission ps
        JOIN ps.student s
        JOIN ps.project p
        JOIN p.week w
        JOIN w.mentorship ms
        JOIN ms.mentor m
        WHERE ps.id = :id
    """)
    Optional<ProjectSubmissionAuthProjection> findAuthProjectionById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ps FROM ProjectSubmission ps WHERE ps.id = :id")
    Optional<ProjectSubmission> findForUpdateById(@Param("id") Long id);

    Page<ProjectSubmission> findByProject_Id(Long projectId, Pageable pageable);
    Optional<ProjectSubmission> findByProject_IdAndStudent_Id(Long projectId, Long studentId);
    boolean existsByProject_IdAndStudent_Id(Long projectId, Long studentId);
    boolean existsByProject_Id(Long projectId);
    /** EB-51 / D-09 — freeze predicate: at least one qualifying (GRADED) submission exists. */
    boolean existsByProject_IdAndStatus(Long projectId, SubmissionStatus status);
    /** EB-51 — cascade-delete protection: rejects deleting week if submissions exist. */
    boolean existsByProject_Week_Id(Long weekId);
    boolean existsByProject_Week_IdAndStatus(Long weekId, SubmissionStatus status);

    @Query("""
        SELECT
            p.publicId                              AS projectId,
            p.title                                 AS title,
            p.brief                                 AS brief,
            p.descriptionUrl                        AS descriptionUrl,
            p.uploadedAttachmentPath                AS attachmentPath,
            p.points                                AS points,
            p.startAt                               AS startAt,
            p.endAt                                 AS endAt,
            p.goal                                  AS goal,
            ps.publicId                             AS submissionId,
            ps.status                               AS submissionStatus,
            ps.finalScore                           AS finalScore,
            p.points                                AS totalPoints,
            ps.fileUrl                              AS fileUrl,
            ps.uploadedFilePath                     AS uploadedFilePath,
            ps.feedBack                             AS feedback,
            m.publicId                              AS mentorId,
            CONCAT(m.firstName, ' ', m.lastName)    AS mentorName,
            m.profileImageUrl                       AS mentorPhoto
        FROM Project p
        JOIN p.week w
        JOIN w.mentorship ms
        JOIN ms.mentor m
        LEFT JOIN p.submissions ps ON ps.student.email = :email
        WHERE p.id = :projectId
    """)
    ProjectWithSubmissionProjection findProjectWithSubmission(
            @Param("projectId") Long projectId,
            @Param("email") String email
    );
    @Query(
            value = """
        SELECT ps
        FROM ProjectSubmission ps
        JOIN FETCH ps.project p
        JOIN FETCH p.week w
        JOIN FETCH w.mentorship m
        JOIN FETCH m.mentor mt
        WHERE ps.student.id = :studentId AND (:status IS NULL OR ps.status = :status)
        ORDER BY ps.submittedAt DESC
    """,
            countQuery = """
        SELECT COUNT(ps)
        FROM ProjectSubmission ps
        JOIN ps.project p
        JOIN p.week w
        JOIN w.mentorship m
        WHERE ps.student.id = :studentId AND (:status IS NULL OR ps.status = :status)
    """
    )
    Page<ProjectSubmission> findForStudentProfile(
            @Param("studentId") Long studentId,
            @Param("status") SubmissionStatus status,
            Pageable pageable
    );

    @Query("""
    SELECT ps
    FROM ProjectSubmission ps
    JOIN FETCH ps.project p
    JOIN FETCH p.week w
    JOIN FETCH w.mentorship m
    JOIN FETCH m.mentor mt
    WHERE ps.student.id = :studentId
      AND ps.status = com.example.gradproj.EduNest.enums.tasks.SubmissionStatus.GRADED
    ORDER BY ps.gradedAt DESC
""")
    Page<ProjectSubmission> findGradedForStudentProfile(
            @Param("studentId") Long studentId,
            Pageable pageable
    );

    @Query("""
        SELECT ps
        FROM ProjectSubmission ps
        JOIN ps.project p
        WHERE ps.student.id = :studentId
          AND p.week.id = :weekId
    """)
    List<ProjectSubmission> findByStudentIdAndWeekId(@Param("studentId") Long studentId,
                                                      @Param("weekId") Long weekId);

    @Query("""
        SELECT ps
        FROM ProjectSubmission ps
        JOIN ps.project p
        WHERE ps.student.id = :studentId
          AND p.week.id IN :weekIds
    """)
    List<ProjectSubmission> findByStudent_IdAndProject_Week_IdIn(@Param("studentId") Long studentId,
                                                                    @Param("weekIds") List<Long> weekIds);

    @Query("""
        SELECT ps.student.id
        FROM ProjectSubmission ps
        WHERE ps.project.id = :projectId
    """)
    List<Long> findStudentIdsByProjectId(@Param("projectId") Long projectId);

    @Query("""
        SELECT ps
        FROM ProjectSubmission ps
        JOIN ps.project p
        JOIN p.week w
        WHERE ps.student.id = :studentId
          AND w.mentorship.id = :mentorshipId
    """)
    List<ProjectSubmission> findByStudentIdAndMentorshipId(@Param("studentId") Long studentId,
                                                           @Param("mentorshipId") Long mentorshipId);
}
