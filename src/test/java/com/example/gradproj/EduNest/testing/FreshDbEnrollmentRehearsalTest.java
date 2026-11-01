package com.example.gradproj.EduNest.testing;

import com.example.gradproj.EduNest.entity.mentorship.Commission;
import com.example.gradproj.EduNest.entity.mentorship.Enrollment;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.RoleRepository;
import com.example.gradproj.EduNest.repository.mentorShip.CommissionRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.users.AdminRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.security.probes.AuthFixture;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("critical")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles({"prod", "test"})
class FreshDbEnrollmentRehearsalTest {

    private static final String UNIQUE_DB = "edunest-eb7-freshrehearsal-"
            + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void freshH2Database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:" + UNIQUE_DB
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthFixture auth;

    @Autowired
    private CommissionRepository commissions;

    @Autowired
    private EnrollmentRepository enrollments;

    @Autowired
    private TotalPointsRepository totalPoints;

    @Autowired
    private StudentRepository students;

    @Autowired
    private MentorRepository mentors;

    @Autowired
    private AdminRepository admins;

    @Autowired
    private MentorShipRepository mentorships;

    @Autowired
    private RoleRepository roles;

    @Test
    @Order(1)
    void freshDatabaseBootsTheAppWithEveryBusinessTableEmpty() throws Exception {
        assertThat(commissions.count()).isZero();
        assertThat(enrollments.count()).isZero();
        assertThat(totalPoints.count()).isZero();
        assertThat(students.count()).isZero();
        assertThat(mentors.count()).isZero();
        assertThat(admins.count()).isZero();
        assertThat(mentorships.count()).isZero();
        long rolesAtBoot = roles.count();
        assertThat(rolesAtBoot).isIn(0L, 3L);

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/commission"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ERR_UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @Order(2)
    void missingCommissionSeedRejectsEnrollmentAndPersistsNothing() throws Exception {
        assertThat(commissions.count()).isZero();
        assertThat(enrollments.count()).isZero();
        assertThat(totalPoints.count()).isZero();
        assertThat(students.count()).isZero();
        assertThat(mentors.count()).isZero();
        assertThat(mentorships.count()).isZero();

        Student student = auth.student("eb7-missing-rate-student@example.org");
        AuthFixture.MentorshipChain chain = auth.mentorship(
                "eb7-missing-rate-mentor@example.org", Status.ACTIVE);
        long mentorshipId = chain.mentorship().getId();

        assertThat(commissions.count()).isZero();

        mockMvc.perform(post("/api/v1/mentorship/{id}/join", mentorshipId)
                        .header("Authorization", auth.authToken(
                                student.getEmail(), AuthFixture.STUDENT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ERR_BUSINESS_RULE"))
                .andExpect(jsonPath("$.message").value("Commission rate not set"));

        assertThat(enrollments.count()).isZero();
        assertThat(totalPoints.count()).isZero();
        assertThat(commissions.count()).isZero();
        assertThat(students.count()).isEqualTo(1);
        assertThat(mentors.count()).isEqualTo(1);
        assertThat(mentorships.count()).isEqualTo(1);
    }

    @Test
    @Order(3)
    void tenPercentFixtureCommissionEnrollsTheStudentWithCorrectMoneySplit() throws Exception {
        Student student = auth.student("eb7-priced-student@example.org");
        AuthFixture.MentorshipChain chain = auth.mentorship(
                "eb7-priced-mentor@example.org", Status.ACTIVE);
        MentorShip mentorShip = chain.mentorship();
        mentorShip.setPrice(200.0);
        mentorShip.setDiscountPercentage(0);
        mentorShip = mentorships.saveAndFlush(mentorShip);

        commissions.saveAndFlush(Commission.builder().rate(10.0).build());
        assertThat(commissions.count()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/mentorship/{id}/join", mentorShip.getId())
                        .header("Authorization", auth.authToken(
                                student.getEmail(), AuthFixture.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.message")
                        .value("Mentorship joined successfully"));

        assertThat(enrollments.count()).isEqualTo(1);
        Enrollment enrollment = enrollments.findAll().get(0);
        assertThat(enrollment.getId()).isNotNull();
        assertThat(enrollment.getPrice()).isEqualTo(200.0, within(1e-9));
        assertThat(enrollment.getPlatformProfit()).isEqualTo(20.0, within(1e-9));
        assertThat(enrollment.getJoinedAt()).isNotNull();
        assertThat(enrollments.existsByMentorShip_IdAndStudent_Id(
                mentorShip.getId(), student.getId())).isTrue();

        assertThat(totalPoints.count()).isEqualTo(1);
        Optional<TotalPoints> tp = totalPoints.findByStudent_IdAndMentorship_Id(
                student.getId(), mentorShip.getId());
        assertThat(tp).isPresent();
        assertThat(tp.get().getTotalPoints()).isZero();
    }
}
