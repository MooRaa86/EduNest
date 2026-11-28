package com.example.gradproj.EduNest.contract;

import com.example.gradproj.EduNest.entity.mentorship.Enrollment;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for Mentor Dashboard endpoints verifying:
 * 1. GET /api/v1/dashboard/students — GROUP BY includes student.publicId and student.profileImageUrl
 *    (prevents PostgreSQL 42803 error) and explicit countQuery computes accurate totalElements.
 * 2. GET /api/v1/dashboard & /api/v1/dashboard/revenue-chart — works both when `months` is omitted (null)
 *    and when provided (prevents PostgreSQL 42P18 untyped null parameter error).
 */
@Tag("critical")
class MentorDashboardContractTest extends ProdChainIntegrationTestBase {

    @Autowired private AuthFixture auth;
    @Autowired private MentorShipRepository mentorshipRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;

    private String mentorToken;
    private Student student;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        String mentorEmail = "dash-mentor-" + suffix + "@example.org";
        Mentor mentor = auth.mentor(mentorEmail);
        mentorToken = auth.authToken(mentorEmail, AuthFixture.MENTOR);

        student = auth.student("dash-student-" + suffix + "@example.org");

        MentorShip activeMs = mentorshipRepository.saveAndFlush(MentorShip.builder()
                .title("Active Course " + suffix)
                .subtitle("sub")
                .description("desc")
                .category("backend")
                .price(100.0)
                .duration(5.0)
                .status(Status.ACTIVE)
                .mentor(mentor)
                .build());

        MentorShip completedMs = mentorshipRepository.saveAndFlush(MentorShip.builder()
                .title("Completed Course " + suffix)
                .subtitle("sub")
                .description("desc")
                .category("backend")
                .price(200.0)
                .duration(5.0)
                .status(Status.COMPLETED)
                .mentor(mentor)
                .build());

        enrollmentRepository.saveAndFlush(Enrollment.builder()
                .student(student)
                .mentorShip(activeMs)
                .price(100.0)
                .platformProfit(10.0)
                .joinedAt(LocalDateTime.now().minusDays(2))
                .build());

        enrollmentRepository.saveAndFlush(Enrollment.builder()
                .student(student)
                .mentorShip(completedMs)
                .price(200.0)
                .platformProfit(20.0)
                .joinedAt(LocalDateTime.now().minusMonths(1))
                .build());
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/students returns grouped student roster with UUID and counts")
    void getStudents_returnsGroupedRosterWithCounts() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/students")
                        .header("Authorization", mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.students.totalElements").value(1))
                .andExpect(jsonPath("$.apiResponse.students.content[0].studentId").value(student.getPublicId().toString()))
                .andExpect(jsonPath("$.apiResponse.students.content[0].email").value(student.getEmail()))
                .andExpect(jsonPath("$.apiResponse.students.content[0].activeMentorshipCount").value(1))
                .andExpect(jsonPath("$.apiResponse.students.content[0].completedMentorshipCount").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/dashboard without months param succeeds (no PostgreSQL 42P18)")
    void getDashboard_withoutMonths_succeeds() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.dashboard.cards.totalStudents").value(1))
                .andExpect(jsonPath("$.apiResponse.dashboard.cards.totalRevenue").value(270.0))
                .andExpect(jsonPath("$.apiResponse.dashboard.salesChart").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/dashboard with months param succeeds")
    void getDashboard_withMonths_succeeds() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard")
                        .param("months", "6")
                        .header("Authorization", mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.dashboard.salesChart").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/revenue-chart works both with and without months param")
    void getRevenueChart_withAndWithoutMonths_succeeds() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/revenue-chart")
                        .header("Authorization", mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse['sales-chart']").isArray());

        mockMvc.perform(get("/api/v1/dashboard/revenue-chart")
                        .param("months", "3")
                        .header("Authorization", mentorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse['sales-chart']").isArray());
    }
}
