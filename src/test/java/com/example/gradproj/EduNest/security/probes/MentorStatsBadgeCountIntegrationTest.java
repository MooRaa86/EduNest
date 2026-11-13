package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.admin.AdminBadge;
import com.example.gradproj.EduNest.entity.admin.UserAdminBadge;
import com.example.gradproj.EduNest.entity.badges.Badge;
import com.example.gradproj.EduNest.entity.badges.BadgeAward;
import com.example.gradproj.EduNest.enums.badge.BadgeCategory;
import com.example.gradproj.EduNest.enums.admin.AdminBadgeType;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.admin.AdminBadgeRepository;
import com.example.gradproj.EduNest.repository.admin.UserAdminBadgeRepository;
import com.example.gradproj.EduNest.repository.badges.BadgeAwardRepository;
import com.example.gradproj.EduNest.repository.badges.BadgeRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EB-54 / P4-05 — MentorStatsProjection.totalBadges semantics.
 *
 * A mentor's totalBadges counts admin-curated badges HELD by the mentor
 * (user_admin_badges), the same semantic AdminUserDirectory displays for
 * both mentors and students. Badges the mentor AWARDED to students
 * (badge_awards.awarded_by) belong to the student and must not be counted.
 * The projection must agree with UserAdminBadgeRepository#findAdminBadgesByUserId,
 * the source AdminUserDirectory uses for its response.
 */
class MentorStatsBadgeCountIntegrationTest extends ProdChainIntegrationTestBase {
    @Autowired private AuthFixture auth;
    @Autowired private MentorRepository mentors;
    @Autowired private AdminBadgeRepository adminBadges;
    @Autowired private UserAdminBadgeRepository userAdminBadges;
    @Autowired private BadgeRepository badges;
    @Autowired private BadgeAwardRepository badgeAwards;

    private AdminBadge newAdminBadge(String name) {
        return adminBadges.saveAndFlush(AdminBadge.builder()
                .name(name).description("eb54 " + name).type(AdminBadgeType.TOP_MENTOR)
                .build());
    }

    @Test
    void zeroBadgesBeforeAnyAward() {
        var mentor = auth.mentor("eb54-badges-zero@example.org");
        assertThat(mentors.getMentorStats(mentor.getId()).getTotalBadges()).isZero();
        assertThat(userAdminBadges.findAdminBadgesByUserId(mentor.getId())).isEmpty();
    }

    @Test
    void countsHeldAdminBadgesButNotBadgesAwardedToStudents() {
        var chain = auth.mentorship("eb54-badges-two@example.org", Status.ACTIVE);
        var mentor = chain.mentor();

        // two admin badges HELD by the mentor (unique per user+badge)
        userAdminBadges.saveAndFlush(UserAdminBadge.builder()
                .user(mentor).adminBadge(newAdminBadge("top-mentor-a")).build());
        userAdminBadges.saveAndFlush(UserAdminBadge.builder()
                .user(mentor).adminBadge(newAdminBadge("top-mentor-b")).build());

        // one badge AWARDED BY the mentor to a student — belongs to the student
        var student = auth.student("eb54-badges-student@example.org");
        Badge badge = badges.saveAndFlush(Badge.builder()
                .mentorship(chain.mentorship()).title("Solver").category(BadgeCategory.ACHIEVEMENT)
                .description("eb54").points(10).build());
        badgeAwards.saveAndFlush(BadgeAward.builder()
                .badge(badge).student(student).awardedBy(mentor).build());

        var stats = mentors.getMentorStats(mentor.getId());
        assertThat(stats.getTotalBadges()).isEqualTo(2);
        // projection agrees with the AdminUserDirectory source of truth
        assertThat(userAdminBadges.findAdminBadgesByUserId(mentor.getId())).hasSize(2);
    }
}
