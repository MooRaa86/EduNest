package com.example.gradproj.EduNest.repository;

import com.example.gradproj.EduNest.entity.mentorship.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthorizationRepository extends JpaRepository<Enrollment, Long> {


    @Query("""
        SELECT COUNT(s) > 0
        FROM Session s
        WHERE s.id = :sessionId
        AND s.week.mentorship.mentor.email = :email
    """)
    Boolean isMentorOwnLiveSession(@Param("sessionId") Long sessionId,
                                   @Param("email") String email);

    @Query("""
        SELECT COUNT(e) > 0
        FROM Enrollment e
        JOIN Week w ON w.mentorship.id = e.mentorShip.id
        JOIN Session s ON s.week.id = w.id
        WHERE s.id = :sessionId
        AND e.student.email = :studentEmail
    """)
    Boolean isStudentEnrolledByLiveSessionId(@Param("studentEmail") String studentEmail,
                                             @Param("sessionId") Long sessionId);

    @Query("""
        SELECT COUNT(q) > 0
        FROM Quiz q
        WHERE q.id = :quizId
        AND q.week.mentorship.mentor.email = :email
    """)
    Boolean isMentorOwnQuiz(@Param("quizId") Long quizId,
                            @Param("email") String email);


    @Query("""
        SELECT COUNT(e) > 0
        FROM Enrollment e
        JOIN Week w ON w.mentorship.id = e.mentorShip.id
        JOIN Quiz q ON q.week.id = w.id
        WHERE q.id = :quizId
        AND e.student.email = :studentEmail
    """)
    Boolean isStudentEnrolledByQuizId(@Param("studentEmail") String studentEmail,
                                      @Param("quizId") Long quizId);


    @Query("""
        SELECT COUNT(q) > 0
        FROM Question q
        WHERE q.id = :questionId
        AND q.quiz.week.mentorship.mentor.email = :email
    """)
    Boolean isMentorOwnQuestion(@Param("questionId") Long questionId,
                                @Param("email") String email);

    @Query("""
        SELECT COUNT(w) > 0
        FROM Week w
        WHERE w.id = :weekId
        AND w.mentorship.mentor.email = :email
    """)
    Boolean isMentorOwnWeek(@Param("weekId") Long weekId,
                            @Param("email") String email);


    @Query("""
        SELECT COUNT(un) > 0
        FROM UserNotification un
        WHERE un.id = :notificationId
        AND un.user.email = :email
    """)
    Boolean isUserOwnNotification(@Param("notificationId") Long notificationId,
                                  @Param("email") String email);


    @Query("""
        SELECT COUNT(e) > 0
        FROM Enrollment e
        JOIN ChatRoom cr ON cr.mentorship.id = e.mentorShip.id
        WHERE cr.id = :roomId
        AND (
            e.student.email = :email
            OR e.mentorShip.mentor.email = :email
        )
    """)
    Boolean isUserMemberOfChatRoom(@Param("roomId") Long roomId,
                                   @Param("email") String email);

}