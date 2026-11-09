package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EB-88 / EB-109 / USR-02 / D-04 — Public mentor profile privacy and Global UUID Migration probe.
 *
 * Verifies that:
 * 1. Mentor profile resolves by opaque UUID public identifier (EB-109).
 * 2. Transitional numeric ID and legacy email lookups continue to work for backwards compatibility.
 * 3. Mentor email is redacted (null) on public profile payloads per D-04.
 * 4. Returned mentorId is serialized as UUID string across profile DTOs and BeforeEnrollData.
 * 5. Non-existent UUIDs, IDs, and emails return 404.
 */
class MentorProfilePrivacyIntegrationTest extends ProdChainIntegrationTestBase {

    @Autowired
    private AuthFixture auth;

    private Long mentorId;
    private UUID mentorPublicId;
    private String mentorEmail;
    private Long mentorshipId;
    private UUID mentorshipPublicId;

    @BeforeEach
    void setUp() {
        mentorEmail = "eb109-mentor-" + System.nanoTime() + "@example.org";
        AuthFixture.MentorshipChain chain = auth.mentorship(mentorEmail, Status.ACTIVE);
        Mentor mentor = chain.mentor();
        MentorShip mentorship = chain.mentorship();
        mentorId = mentor.getId();
        mentorPublicId = mentor.getPublicId();
        mentorshipId = mentorship.getId();
        mentorshipPublicId = mentorship.getPublicId();
    }

    @Test
    @DisplayName("GET /api/v1/profile/mentor/{uuid} resolves by UUID publicId and redacts mentorEmail")
    void getMentorProfile_byUuid_returns200_andRedactsEmail() throws Exception {
        mockMvc.perform(get("/api/v1/profile/mentor/" + mentorPublicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.mentorProfile.mentorId").value(mentorPublicId.toString()))
                .andExpect(jsonPath("$.apiResponse.mentorProfile.publicId").value(mentorPublicId.toString()))
                .andExpect(jsonPath("$.apiResponse.mentorProfile.mentorFirstName").value("Probe"))
                .andExpect(jsonPath("$.apiResponse.mentorProfile.mentorEmail").value(nullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/profile/mentor/{id} resolves by numeric ID bridge and returns UUID mentorId")
    void getMentorProfile_byNumericId_returns200_andReturnsUuidMentorId() throws Exception {
        mockMvc.perform(get("/api/v1/profile/mentor/" + mentorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.mentorProfile.mentorId").value(mentorPublicId.toString()))
                .andExpect(jsonPath("$.apiResponse.mentorProfile.mentorFirstName").value("Probe"))
                .andExpect(jsonPath("$.apiResponse.mentorProfile.mentorEmail").value(nullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/profile/mentor/{email} resolves by legacy email for backwards compatibility")
    void getMentorProfile_byLegacyEmail_returns200_andResolvesMentorIdAsUuid() throws Exception {
        mockMvc.perform(get("/api/v1/profile/mentor/" + mentorEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.mentorProfile.mentorId").value(mentorPublicId.toString()))
                .andExpect(jsonPath("$.apiResponse.mentorProfile.mentorFirstName").value("Probe"))
                .andExpect(jsonPath("$.apiResponse.mentorProfile.mentorEmail").value(nullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/profile/mentor/{uuid}/mentorships resolves by UUID publicId")
    void getMentorMentorships_byUuid_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/profile/mentor/" + mentorPublicId + "/mentorships")
                        .param("size", "5")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.mentorships.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.apiResponse.mentorships.content[0].id").value(mentorshipPublicId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/profile/mentor/{uuid}/reviews resolves by UUID publicId")
    void getMentorReviews_byUuid_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/profile/mentor/" + mentorPublicId + "/reviews")
                        .param("size", "5")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.reviews.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/profile/mentor/{uuid}/full resolves by UUID publicId")
    void getMentorFullProfile_byUuid_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/profile/mentor/" + mentorPublicId + "/full"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.profile.mentorProfile.mentorId").value(mentorPublicId.toString()))
                .andExpect(jsonPath("$.apiResponse.profile.mentorProfile.mentorEmail").value(nullValue()))
                .andExpect(jsonPath("$.apiResponse.profile.mentorships.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("GET /api/v1/profile/mentor/{uuid} with non-existent UUID returns 404")
    void getMentorProfile_nonExistentUuid_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/profile/mentor/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
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
    @DisplayName("GET /api/v1/student/mentorships/{id}/overview exposes mentorId as UUID in BeforeEnrollData")
    void getMentorshipOverview_beforeEnroll_containsUuidMentorId() throws Exception {
        mockMvc.perform(get("/api/v1/student/mentorships/" + mentorshipId + "/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiResponse.mentorship.beforeEnroll.mentorId").value(mentorPublicId.toString()))
                .andExpect(jsonPath("$.apiResponse.mentorship.beforeEnroll.publicId").value(mentorshipPublicId.toString()))
                .andExpect(jsonPath("$.apiResponse.mentorship.beforeEnroll.mentorName").value(containsString("Probe")));
    }
}
