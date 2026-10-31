package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.mentorship.MentorShipReviews;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.mentorShip.ReviewsRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.studentMentorship.MentorshipLeaderboardService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeaderboardPrivacyTest extends ProdChainIntegrationTestBase {
    private static final String OWNER = "eb23-owner@example.org", OTHER_MENTOR = "eb23-other-mentor@example.org";
    private static final String FIRST = "eb23-first@example.org", SECOND = "eb23-second@example.org";
    private static final String OUTSIDER = "eb23-outsider@example.org", FOREIGN = "eb23-foreign@example.org", ADMIN = "eb23-admin@example.org";
    @Autowired private AuthFixture auth;
    @Autowired private TotalPointsRepository points;
    @Autowired private StudentRepository students;
    @Autowired private ReviewsRepository reviews;
    @Autowired private MentorshipLeaderboardService service;
    @Autowired private ObjectMapper json;
    private long mentorshipId;
    private String firstName, secondName;

    @BeforeEach void seed() {
        var course = auth.mentorship(OWNER, Status.ACTIVE).mentorship();
        mentorshipId = course.getId();
        var other = auth.mentorship(OTHER_MENTOR, Status.ACTIVE).mentorship();
        auth.enroll(mentorshipId, FIRST);
        auth.enroll(mentorshipId, SECOND);
        auth.enroll(other.getId(), FOREIGN);
        auth.student(OUTSIDER);
        auth.admin(ADMIN);
        var first = auth.student(FIRST);
        first.setProfileImageUrl("https://example.org/private-image");
        students.saveAndFlush(first);
        var second = auth.student(SECOND);
        firstName = first.getFirstName() + " " + first.getLastName();
        secondName = second.getFirstName() + " " + second.getLastName();
        points.saveAndFlush(TotalPoints.builder().student(first).mentorship(course).totalPoints(100).build());
        points.saveAndFlush(TotalPoints.builder().student(second).mentorship(course).totalPoints(20).build());
        reviews.saveAndFlush(MentorShipReviews.builder().student(first).mentorShip(course).rating(5).feedBack("Helpful").build());
    }

    private String route(long id) { return "/api/v1/student/mentorships/" + id + "/leaderboard"; }
    private String token(String email, String role) { return auth.authToken(email, role); }

    @Test void productionChainRoleMatrixAndInvalidMentorship() throws Exception {
        mockMvc.perform(get(route(mentorshipId))).andExpect(status().isUnauthorized());
        for (String email : List.of(OUTSIDER, FOREIGN))
            mockMvc.perform(get(route(mentorshipId)).header("Authorization", token(email, AuthFixture.STUDENT))).andExpect(status().isForbidden());
        mockMvc.perform(get(route(mentorshipId)).header("Authorization", token(OTHER_MENTOR, AuthFixture.MENTOR))).andExpect(status().isForbidden());
        for (String[] principal : new String[][]{{FIRST, AuthFixture.STUDENT}, {OWNER, AuthFixture.MENTOR}, {ADMIN, AuthFixture.ADMIN}})
            mockMvc.perform(get(route(mentorshipId)).header("Authorization", token(principal[0], principal[1]))).andExpect(status().isOk());
        mockMvc.perform(get(route(999999999L)).header("Authorization", token(ADMIN, AuthFixture.ADMIN))).andExpect(status().isForbidden());
        mockMvc.perform(get(route(-1L)).header("Authorization", token(FIRST, AuthFixture.STUDENT))).andExpect(status().isForbidden());
    }

    @Test void serializedEntriesFollowAmendedD04ContractWithOrderingAndPagination() throws Exception {
        String body = mockMvc.perform(get(route(mentorshipId)).param("size", "1").param("page", "0")
                .header("Authorization", token(SECOND, AuthFixture.STUDENT))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode board = json.readTree(body).path("apiResponse").path("leaderboard");
        JsonNode page = board.path("leaderboard"), entries = page.path("content");
        assertThat(page.path("page").asInt()).isZero();
        assertThat(page.path("size").asInt()).isEqualTo(1);
        assertThat(page.path("totalElements").asInt()).isEqualTo(2);
        assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        assertThat(board.path("userRank").asLong()).isEqualTo(2);
        assertThat(entries.size()).isEqualTo(2); // existing contract includes the off-page current student
        assertThat(entries.get(0).path("studentFullName").asText()).isEqualTo(firstName);
        assertThat(entries.get(0).path("studentEmail").asText()).isEqualTo(FIRST);
        assertThat(entries.get(0).path("studentProfileImageUrl").asText()).isEqualTo("https://example.org/private-image");
        assertThat(entries.get(0).path("rank").asLong()).isEqualTo(1);
        assertThat(entries.get(0).path("totalPoints").asInt()).isEqualTo(100);
        assertThat(entries.get(1).path("studentFullName").asText()).isEqualTo(secondName);
        assertThat(entries.get(1).path("studentEmail").asText()).isEqualTo(SECOND);
        assertThat(entries.get(1).path("rank").asLong()).isEqualTo(2);
        assertThat(entries.get(1).path("totalPoints").asInt()).isEqualTo(20);
        for (JsonNode entry : entries) assertEntryFields(entry);
        JsonNode currentUser = board.path("currentUser");
        assertEntryFields(currentUser);
        assertThat(currentUser.path("studentEmail").asText()).isEqualTo(SECOND);
        assertThat(currentUser.path("rank").asLong()).isEqualTo(2);
        // D-04 amendment: studentLevel and badge fields stay excluded from the
        // serialized contract even though the internal row projection has them.
        assertThat(body).doesNotContain("studentLevel", "lastBadge", "phone", "userEmail");
    }

    private void assertEntryFields(JsonNode node) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("studentFullName", "studentEmail", "studentProfileImageUrl", "rank", "totalPoints");
    }

    @Test void directServiceRejectsSpoofedEmailAndMissingId() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(OUTSIDER, "n/a", List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        try {
            assertThatThrownBy(() -> service.getMentorshipLeaderboard(mentorshipId, 10, 0, FIRST)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.getMentorshipLeaderboard(null, 10, 0, FIRST)).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void publicReviewPayloadsKeepOriginalContractEmailAndImageRestore() throws Exception {
        // Backwards-compatibility contract with the separate FE project: public
        // review cards expose the reviewer email + profile image under the
        // original per-surface property names until FE usage is verified —
        // both restorations are PENDING Lead/PO approval (D-04).
        String mentorshipRoute = "/api/v1/student/mentorships/" + mentorshipId + "/reviews";
        String profileRoute = "/api/v1/profile/mentor/" + OWNER + "/reviews";
        for (String[] pathAndEmailKey : new String[][]{{mentorshipRoute, "studentEmail", "feedback", "reviewId"}, {profileRoute, "userEmail", "feedBack", "id"}}) {
            String body = mockMvc.perform(get(pathAndEmailKey[0])).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode cards = json.readTree(body).path("apiResponse").path("reviews").path("content");
            assertThat(body).contains(firstName, "Helpful", "userProfileImageUrl", "https://example.org/private-image", pathAndEmailKey[1], FIRST);
            boolean reviewCard = false;
            for (JsonNode card : cards) {
                if (card.path(pathAndEmailKey[2]).asText("").equals("Helpful")) {
                    assertThat(card.path(pathAndEmailKey[1]).asText()).isEqualTo(FIRST);
                    assertThat(card.path("userProfileImageUrl").asText()).isEqualTo("https://example.org/private-image");
                    assertThat(card.hasNonNull(pathAndEmailKey[3])).isTrue();
                    reviewCard = true;
                }
            }
            assertThat(reviewCard).isTrue();
        }
    }
}
