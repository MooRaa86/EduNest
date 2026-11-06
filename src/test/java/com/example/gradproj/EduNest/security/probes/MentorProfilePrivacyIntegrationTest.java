package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EB-88 / USR-02 / D-04 — Public mentor profile privacy and dual-identifier probe.
 *
 * Verifies that:
 * 1. Mentor profile can be retrieved using numeric ID as primary identifier.
 * 2. Legacy mentor email lookup continues to work during the transition period.
 * 3. Mentor email is redacted (null) on public profile payloads per D-04.
 * 4. Mentorship overview BeforeEnrollData exposes mentorId for frontend linkage.
 * 5. Non-existent IDs and emails return 404.
 */
class MentorProfilePrivacyIntegrationTest extends ProdChainIntegrationTestBase {

    @Autowired
    private AuthFixture auth;

    private Long mentorId;
    private String mentorEmail;
    private Long mentorshipId;

    @BeforeEach
    void setUp() {
        mentorEmail = "eb88-mentor-" + System.nanoTime() + "@example.org";
        AuthFixture.MentorshipChain chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        Mentor mentor = chain.mentor();
        MentorShip mentorship = chain.mentorship();
        mentorId = mentor.getId();
        mentorshipId = mentorship.getId();
    }

    @Test
    @DisplayName("GET /api/v1/profile/mentor/{id} resolves by numeric ID and redacts mentorEmail")
    void getMentorProfile_byNumericId_returns200_andRedactsEmail() throws Exception {
        mockMvc.perform(get("/api/v1/profile/mentor/" + mentorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.mentorProfile.mentorId").value(mentorId))
                .andExpect(jsonPath("$.apiResponse.mentorProfile.mentorFirstName").value("Probe"))
                .andExpect(jsonPath("$.apiResponse.mentorProfile.mentorEmail").value(nullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/profile/mentor/{email} resolves by legacy email for backwards compatibility")
    void getMentorProfile_byLegacyEmail_returns200_andResolvesMentorId() throws Exception {
        mockMvc.perform(get("/api/v1/profile/mentor/" + mentorEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.mentorProfile.mentorId").value(mentorId))
                .andExpect(jsonPath("$.apiResponse.mentorProfile.mentorFirstName").value("Probe"))
                .andExpect(jsonPath("$.apiResponse.mentorProfile.mentorEmail").value(nullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/profile/mentor/{id}/mentorships resolves by numeric ID")
    void getMentorMentorships_byNumericId_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/profile/mentor/" + mentorId + "/mentorships")
                        .param("size", "5")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.mentorships.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.apiResponse.mentorships.content[0].id").value(mentorshipId));
    }

    @Test
    @DisplayName("GET /api/v1/profile/mentor/{id}/reviews resolves by numeric ID")
    void getMentorReviews_byNumericId_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/profile/mentor/" + mentorId + "/reviews")
                        .param("size", "5")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.reviews.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/profile/mentor/{id}/full resolves by numeric ID")
    void getMentorFullProfile_byNumericId_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/profile/mentor/" + mentorId + "/full"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.profile.mentorProfile.mentorId").value(mentorId))
                .andExpect(jsonPath("$.apiResponse.profile.mentorProfile.mentorEmail").value(nullValue()))
                .andExpect(jsonPath("$.apiResponse.profile.mentorships.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("GET /api/v1/profile/mentor/{id} with non-existent ID returns 404")
    void getMentorProfile_nonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/profile/mentor/999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/profile/mentor/{email} with non-existent email returns 404")
    void getMentorProfile_nonExistentEmail_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/profile/mentor/nonexistent-mentor@example.org"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/student/mentorships/{id}/overview exposes mentorId in BeforeEnrollData")
    void getMentorshipOverview_beforeEnroll_containsMentorId() throws Exception {
        mockMvc.perform(get("/api/v1/student/mentorships/" + mentorshipId + "/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.mentorship.beforeEnroll.mentorId").value(mentorId))
                .andExpect(jsonPath("$.apiResponse.mentorship.beforeEnroll.mentorName").value(containsString("Probe")));
    }
}
