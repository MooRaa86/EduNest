package com.example.gradproj.EduNest.service.quiz;

import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.quiz.Question;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.entity.quiz.QuizSubmission;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.quiz.AnswerChoices;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.repository.quiz.QuestionRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EB-60 / P4-11 — N+1 query-budget integration suite (quiz read paths).
 *
 * Measures real SQL statement counts through the full MockMvc/OSIV production
 * chain (statistics enabled only in this context via @TestPropertySource), and
 * captures raw SQL text through a Hibernate StatementInspector so pagination
 * behavior (LIMIT vs in-memory) is observable, not assumed.
 *
 * Baseline phase (before F1/F2/F3 fixes): prints observed counts.
 * After the fixes: the same methods assert query ceilings and pagination shape.
 */
@Tag("performance")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(QuizN1QueryBudgetIntegrationTest.SqlCaptureConfig.class)
public class QuizN1QueryBudgetIntegrationTest extends ProdChainIntegrationTestBase {

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlCaptureConfig implements org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer {
        public static final List<String> SQL = new CopyOnWriteArrayList<>();
        @Override
        public void customize(java.util.Map<String, Object> hibernateProperties) {
            hibernateProperties.put("hibernate.session_factory.statement_inspector",
                    (StatementInspector) sql -> {
                        SQL.add(sql);
                        return sql;
                    });
        }
    }

    @Autowired private AuthFixture auth;
    @Autowired private WeekRepository weeks;
    @Autowired private QuizRepository quizzes;
    @Autowired private QuestionRepository questions;
    @Autowired private QuizSubmissionRepository submissions;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private com.example.gradproj.EduNest.service.homepage.HomePageService homePage;

    private Statistics stats;

    @BeforeEach
    void initStats() {
        SessionFactory sf = entityManagerFactory.unwrap(SessionFactory.class);
        stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);
    }

    private record Measurement(long statements, long hqlQueries, List<String> sql,
                               ListAppender<ILoggingEvent> hibernateLogs) {
        long dataQueries() {
            return sql.stream().filter(s -> !s.trim().toLowerCase().startsWith("select count(")).count();
        }
        long limitQueries() {
            return sql.stream().filter(s -> s.toLowerCase().contains(" limit ")).count();
        }
        long submissionJoinQueries() {
            return sql.stream().filter(s -> s.toLowerCase().contains("quiz_submission") && s.toLowerCase().contains("join")).count();
        }
        long userJoinQueries() {
            return sql.stream().filter(s -> s.toLowerCase().contains("users") && s.toLowerCase().contains("join")).count();
        }
    }

    private Measurement measure(ThrowingRunnable r) throws Exception {
        stats.clear();
        SqlCaptureConfig.SQL.clear();
        ListAppender<ILoggingEvent> appender = attachHibernateQueryCapture();
        try {
            r.run();
        } finally {
            ((Logger) org.slf4j.LoggerFactory.getLogger("org.hibernate")).detachAppender(appender);
        }
        return new Measurement(stats.getPrepareStatementCount(), stats.getQueryExecutionCount(),
                List.copyOf(SqlCaptureConfig.SQL), appender);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * HHH000104 ("applying in memory") is the observable signature of
     * collection fetch join + Page; if it fires during a measurement, the
     * page was materialized in memory instead of being LIMITed by the DB.
     */
    private ListAppender<ILoggingEvent> attachHibernateQueryCapture() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger hibernateRoot = (Logger) org.slf4j.LoggerFactory.getLogger("org.hibernate");
        hibernateRoot.addAppender(appender);
        return appender;
    }

    private void assertNoInMemoryPagination(ListAppender<ILoggingEvent> appender) {
        assertThat(appender.list)
                .noneMatch(e -> e.getFormattedMessage().contains("applying in memory")
                        || e.getFormattedMessage().contains("HHH000104"));
    }

    private void log(String label, Measurement m) {
        System.out.println("[EB60-BASELINE] " + label
                + " statements=" + m.statements()
                + " hql=" + m.hqlQueries()
                + " limitQueries=" + m.limitQueries()
                + " submissionJoinQueries=" + m.submissionJoinQueries());
        m.sql().forEach(s -> System.out.println("[EB60-SQL] " + s.replaceAll("\\s+", " ").trim()));
    }

    /* ---------- fixtures ---------- */

    private Quiz seedQuiz(Week week, String title, QuizStatus status) {
        Quiz quiz = quizzes.saveAndFlush(Quiz.builder()
                .week(week).title(title).description("EB-60 fixture")
                .durationMinutes(30).status(status).build());
        for (int i = 1; i <= 2; i++) {
            questions.saveAndFlush(Question.builder()
                    .quiz(quiz).text("Q" + i + " " + title).points(10)
                    .correctAnswer(AnswerChoices.A)
                    .optionA("A").optionB("B").optionC("C").optionD("D").build());
        }
        return quiz;
    }

    private void seedSubmission(Quiz quiz, Student student, Double score) {
        submissions.saveAndFlush(QuizSubmission.builder()
                .quiz(quiz).student(student).score(score).startDate(java.time.LocalDateTime.now()).build());
    }

    /* ---------- F1: dashboard ---------- */

    @Test
    @DisplayName("F1 baseline: dashboard over 6 vs 12 quizzes (N+1 growth evidence)")
    void f1_dashboard_growth_baseline() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        var chain6 = auth.mentorship("eb60-f1-a-m-" + unique + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        Week week6 = weeks.saveAndFlush(Week.builder().mentorship(chain6.mentorship()).title("W6").build());
        for (int i = 1; i <= 6; i++) {
            Quiz q = seedQuiz(week6, "Q" + i, QuizStatus.PUBLISHED);
            seedSubmission(q, auth.student("eb60-f1-a-s" + i + "-" + unique + "@example.org"), 10.0);
            seedSubmission(q, auth.student("eb60-f1-a-s" + i + "b-" + unique + "@example.org"), 9.0);
        }
        quizzes.saveAndFlush(Quiz.builder().week(week6).title("DRAFTQ").description("d")
                .durationMinutes(5).status(QuizStatus.DRAFT).build());

        var chain12 = auth.mentorship("eb60-f1-b-m-" + unique + "@example.org", com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        Week week12 = weeks.saveAndFlush(Week.builder().mentorship(chain12.mentorship()).title("W12").build());
        for (int i = 1; i <= 12; i++) {
            Quiz q = seedQuiz(week12, "Q" + i, QuizStatus.PUBLISHED);
            seedSubmission(q, auth.student("eb60-f1-b-s" + i + "-" + unique + "@example.org"), 8.0);
        }

        String mentorToken6 = auth.authToken("eb60-f1-a-m-" + unique + "@example.org", AuthFixture.MENTOR);
        String mentorToken12 = auth.authToken("eb60-f1-b-m-" + unique + "@example.org", AuthFixture.MENTOR);
        String dashboard6 = "/api/v1/quiz/dashboard/" + chain6.mentorship().getPublicId();
        String dashboard12 = "/api/v1/quiz/dashboard/" + chain12.mentorship().getPublicId();

        Measurement m6 = measure(() -> mockMvc.perform(get(dashboard6).header("Authorization", mentorToken6))
                .andExpect(status().isOk()));
        Measurement m12 = measure(() -> mockMvc.perform(get(dashboard12).header("Authorization", mentorToken12))
                .andExpect(status().isOk()));

        log("F1 dashboard 6 quizzes + 1 draft", m6);
        log("F1 dashboard 12 quizzes", m12);

        // EB-60 fixed behavior: statement count no longer grows with quiz
        // count (baseline was 12 vs 17, i.e. +5 per 6 quizzes).
        assertThat(m6.statements()).isLessThanOrEqualTo(6);
        assertThat(m12.statements()).isLessThanOrEqualTo(6);
        assertThat(m6.statements()).isEqualTo(m12.statements());
    }

    /* ---------- F2: paginated quiz lists ---------- */

    @Test
    @DisplayName("F2 baseline: mentor quiz filter page in-memory pagination + per-row submission access")
    void f2_quizFilter_baseline() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String mentorEmail = "eb60-f2-m-" + unique + "@example.org";
        var chain = auth.mentorship(mentorEmail, com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        Week week = weeks.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("W").build());
        for (int i = 1; i <= 6; i++) {
            Quiz q = seedQuiz(week, "Quiz " + i + " " + unique, QuizStatus.PUBLISHED);
            seedSubmission(q, auth.student("eb60-f2-s" + i + "-" + unique + "@example.org"), 10.0);
        }
        seedQuiz(week, "Draft Quiz " + unique, QuizStatus.DRAFT);

        String mentorToken = auth.authToken(mentorEmail, AuthFixture.MENTOR);
        String base = "/api/v1/quiz/filter/" + chain.mentorship().getPublicId();

        Measurement smallPage = measure(() -> mockMvc.perform(get(base)
                        .param("page", "0").param("size", "2").header("Authorization", mentorToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.apiResponse.Quizzes.content.length()").value(2))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.apiResponse.Quizzes.totalElements").value(7))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.apiResponse.Quizzes.totalPages").value(4)));
        Measurement page2 = measure(() -> mockMvc.perform(get(base)
                        .param("page", "1").param("size", "2").header("Authorization", mentorToken))
                .andExpect(status().isOk()));
        Measurement fullPage = measure(() -> mockMvc.perform(get(base)
                        .param("page", "0").param("size", "6").header("Authorization", mentorToken))
                .andExpect(status().isOk()));

        log("F2 mentor filter page0 size2", smallPage);
        log("F2 mentor filter page1 size2", page2);
        log("F2 mentor filter page0 size6", fullPage);

        // EB-60 fixed behavior: DB-level pagination (no HHH000104 in-memory
        // pagination), constant query budget, page 2 returns different rows.
        assertNoInMemoryPagination(smallPage.hibernateLogs());
        assertThat(smallPage.statements()).isLessThanOrEqualTo(6);
        assertThat(page2.statements()).isLessThanOrEqualTo(6);
        assertThat(fullPage.statements()).isLessThanOrEqualTo(6);
        String page0Body = mockMvc.perform(get(base).param("page", "0").param("size", "2")
                        .header("Authorization", mentorToken)).andReturn().getResponse().getContentAsString();
        String page1Body = mockMvc.perform(get(base).param("page", "1").param("size", "2")
                        .header("Authorization", mentorToken)).andReturn().getResponse().getContentAsString();
        assertThat(page1Body).isNotEqualTo(page0Body);
    }

    @Test
    @DisplayName("F2 baseline: student quizzes excludes DRAFT, same fetch-join shape")
    void f2_studentQuizzes_baseline() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String mentorEmail = "eb60-f2s-m-" + unique + "@example.org";
        String studentEmail = "eb60-f2s-s-" + unique + "@example.org";
        var chain = auth.mentorship(mentorEmail, com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        auth.enroll(chain.mentorship().getId(), studentEmail);
        Week week = weeks.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("W").build());
        for (int i = 1; i <= 5; i++) {
            Quiz q = seedQuiz(week, "SQuiz " + i + " " + unique, QuizStatus.PUBLISHED);
            seedSubmission(q, auth.student(studentEmail), i == 1 ? 10.0 : null);
        }
        seedQuiz(week, "SDraft " + unique, QuizStatus.DRAFT);

        String studentToken = auth.authToken(studentEmail, AuthFixture.STUDENT);
        String base = "/api/v1/quiz/student-quizzes/" + chain.mentorship().getPublicId();

        Measurement page = measure(() -> mockMvc.perform(get(base)
                        .param("page", "0").param("size", "3").header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.apiResponse.Quizzes.content.length()").value(3))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.apiResponse.Quizzes.totalElements").value(5)));
        log("F2 student quizzes page0 size3", page);

        // EB-60 fixed behavior: DRAFT still excluded, DB pagination, no N+1
        assertNoInMemoryPagination(page.hibernateLogs());
        assertThat(page.statements()).isLessThanOrEqualTo(6);
    }

    @Test
    @DisplayName("F2 baseline: mentorshipQuizzesOverview compounds dashboard + page")
    void f2_overview_baseline() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String mentorEmail = "eb60-f2o-m-" + unique + "@example.org";
        var chain = auth.mentorship(mentorEmail, com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        Week week = weeks.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("W").build());
        for (int i = 1; i <= 6; i++) {
            Quiz q = seedQuiz(week, "OQuiz " + i + " " + unique, QuizStatus.PUBLISHED);
            seedSubmission(q, auth.student("eb60-f2o-s" + i + "-" + unique + "@example.org"), 7.0);
        }
        String mentorToken = auth.authToken(mentorEmail, AuthFixture.MENTOR);
        String base = "/api/v1/quiz/mentorshipQuizzesOverview/" + chain.mentorship().getPublicId();

        Measurement page = measure(() -> mockMvc.perform(get(base)
                        .param("page", "0").param("size", "3").header("Authorization", mentorToken))
                .andExpect(status().isOk()));
        log("F2 overview page0 size3 (6 quizzes total)", page);

        // EB-60 fixed behavior: baseline was 15 statements (dashboard N+1 +
        // page); now bounded regardless of quiz count.
        assertNoInMemoryPagination(page.hibernateLogs());
        assertThat(page.statements()).isLessThanOrEqualTo(10);
    }

    /* ---------- F3: submission listings ---------- */

    @Test
    @DisplayName("F3 baseline: submissions-by-quiz per-row student fetch (2 vs 6 rows)")
    void f3_submissionsByQuiz_baseline() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String mentorEmail = "eb60-f3-m-" + unique + "@example.org";
        var chain = auth.mentorship(mentorEmail, com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        Week week = weeks.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("W").build());
        Quiz qSmall = seedQuiz(week, "Small " + unique, QuizStatus.PUBLISHED);
        Quiz qLarge = seedQuiz(week, "Large " + unique, QuizStatus.PUBLISHED);
        for (int i = 1; i <= 6; i++) {
            Student s = auth.student("eb60-f3-s" + i + "-" + unique + "@example.org");
            seedSubmission(qLarge, s, i % 2 == 0 ? 10.0 : null);
            if (i <= 2) seedSubmission(qSmall, s, 10.0);
        }

        String mentorToken = auth.authToken(mentorEmail, AuthFixture.MENTOR);

        Measurement small = measure(() -> mockMvc.perform(get("/api/v1/submissions/quiz/" + qSmall.getPublicId())
                        .param("page", "0").param("size", "2").header("Authorization", mentorToken))
                .andExpect(status().isOk()));
        Measurement large = measure(() -> mockMvc.perform(get("/api/v1/submissions/quiz/" + qLarge.getPublicId())
                        .param("page", "0").param("size", "6").header("Authorization", mentorToken))
                .andExpect(status().isOk()));

        log("F3 submissions-by-quiz 2 rows", small);
        log("F3 submissions-by-quiz 6 rows", large);

        // EB-60 fixed behavior: baseline was 9 vs 13 (one extra students
        // select per row). Now constant, and the student rows arrive joined
        // in the page query (EntityGraph), not per-row.
        assertThat(small.userJoinQueries()).isGreaterThan(0);
        assertThat(large.statements()).isLessThanOrEqualTo(8);
        assertThat(small.statements()).isEqualTo(large.statements());
    }

    @Test
    @DisplayName("F3 baseline: submissions-by-student per-row student fetch")
    void f3_submissionsByStudent_baseline() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String mentorEmail = "eb60-f3b-m-" + unique + "@example.org";
        String studentEmail = "eb60-f3b-s-" + unique + "@example.org";
        var chain = auth.mentorship(mentorEmail, com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        auth.enroll(chain.mentorship().getId(), studentEmail);
        Student student = auth.student(studentEmail);
        Week week = weeks.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("W").build());
        for (int i = 1; i <= 4; i++) {
            Quiz q = seedQuiz(week, "BQuiz " + i + " " + unique, QuizStatus.PUBLISHED);
            seedSubmission(q, student, i == 2 ? null : 10.0);
        }

        String mentorToken = auth.authToken(mentorEmail, AuthFixture.MENTOR);
        Measurement m = measure(() -> mockMvc.perform(get("/api/v1/submissions/student/" + student.getPublicId())
                        .param("page", "0").param("size", "4").header("Authorization", mentorToken))
                .andExpect(status().isOk()));
        log("F3 submissions-by-student 4 rows", m);

        // EB-60 fixed behavior: baseline was 8 (per-row student fetches).
        assertThat(m.userJoinQueries()).isGreaterThan(0);
        assertThat(m.statements()).isLessThanOrEqualTo(8);
    }

    /* ---------- F4: continue-learning publicId ---------- */

    @Test
    @DisplayName("F4: homepage continue-learning carries publicId inline (no per-row lookup)")
    void f4_continueLearning_publicId_inline() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String mentorEmail = "eb60-f4-m-" + unique + "@example.org";
        String studentEmail = "eb60-f4-s-" + unique + "@example.org";
        var chain = auth.mentorship(mentorEmail, com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        auth.enroll(chain.mentorship().getId(), studentEmail);

        Measurement run1 = measure(() -> {
            var response = homePage.getStudentHomePage(studentEmail);
            // one publicId lookup per continue-learning row in the old code (limit = 2)
            assertThat(response.getContinueLearning()).isNotEmpty();
        });
        log("F4 getStudentHomePage run1 (1 continue-learning row)", run1);

        Measurement run2 = measure(() -> {
            var response = homePage.getStudentHomePage(studentEmail);
            assertThat(response.getContinueLearning().get(0).getMentorshipId())
                    .isEqualTo(chain.mentorship().getPublicId());
        });
        log("F4 getStudentHomePage run2", run2);

        // EB-60 fixed behavior: publicId is carried by the projection
        // (asserted above); baseline with the old per-row lookup was 7
        // statements, the fixed path is 6 — and it does NOT grow with the
        // continue-learning limit.
        assertThat(run1.statements()).isLessThanOrEqualTo(6);
        assertThat(run2.statements()).isLessThanOrEqualTo(6);
    }

    @Test
    @DisplayName("Dashboard, pages and submissions keep their response contracts")
    void contracts_hold() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String mentorEmail = "eb60-ct-m-" + unique + "@example.org";
        String studentEmail = "eb60-ct-s-" + unique + "@example.org";
        var chain = auth.mentorship(mentorEmail, com.example.gradproj.EduNest.enums.mentorShip.Status.ACTIVE);
        auth.enroll(chain.mentorship().getId(), studentEmail);
        Week week = weeks.saveAndFlush(Week.builder().mentorship(chain.mentorship()).title("W").build());

        Quiz scored = seedQuiz(week, "Scored " + unique, QuizStatus.PUBLISHED);
        Student s1 = auth.student(studentEmail);
        Student s2 = auth.student("eb60-ct-s2-" + unique + "@example.org");
        auth.enroll(chain.mentorship().getId(), "eb60-ct-s2-" + unique + "@example.org");
        seedSubmission(scored, s1, 10.0);
        seedSubmission(scored, s2, 9.0);
        seedQuiz(week, "Empty " + unique, QuizStatus.PUBLISHED);
        seedQuiz(week, "Hidden " + unique, QuizStatus.DRAFT);

        String mentorToken = auth.authToken(mentorEmail, AuthFixture.MENTOR);
        String studentToken = auth.authToken(studentEmail, AuthFixture.STUDENT);

        mockMvc.perform(get("/api/v1/quiz/dashboard/" + chain.mentorship().getPublicId())
                        .header("Authorization", mentorToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.apiResponse.['Dashboard Details'].totalQuizzes").value(3))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.apiResponse.['Dashboard Details'].publishedCount").value(2))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.apiResponse.['Dashboard Details'].draftCount").value(1))
                // semantics preserved from baseline: sum of per-quiz rounded averages
                // / ALL quizzes (including DRAFT and empty), final value NOT re-rounded
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.apiResponse.['Dashboard Details'].averageScore").value(3.1666666666666665));

        mockMvc.perform(get("/api/v1/quiz/student-quizzes/" + chain.mentorship().getPublicId())
                        .header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.apiResponse.Quizzes.totalElements").value(2));

        mockMvc.perform(get("/api/v1/submissions/quiz/" + scored.getPublicId())
                        .header("Authorization", mentorToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.apiResponse.Submissions.length()").value(2))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.apiResponse.Submissions[0].totalPoints").value(20));

        // foreign mentor cannot read
        auth.mentor("eb60-ct-foreign-" + unique + "@example.org");
        String foreignToken = auth.authToken("eb60-ct-foreign-" + unique + "@example.org", AuthFixture.MENTOR);
        mockMvc.perform(get("/api/v1/submissions/quiz/" + scored.getPublicId())
                        .header("Authorization", foreignToken))
                .andExpect(status().isForbidden());
    }
}
