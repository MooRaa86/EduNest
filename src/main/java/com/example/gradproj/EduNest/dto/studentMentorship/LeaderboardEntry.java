package com.example.gradproj.EduNest.dto.studentMentorship;

/**
 * Enrollment-restricted leaderboard row. D-04 amendment (Ruler B,
 * Engineering Lead-approved): the leaderboard is not public — it is readable
 * only by enrolled students, the owning mentor and ADMIN — so rows carry the
 * exact five-field contract below. This amendment supersedes the earlier
 * name-and-points-only leaderboard-row rule; public review surfaces keep the
 * display-name-only privacy rule.
 */
public record LeaderboardEntry(
        String studentFullName,
        String studentEmail,
        String studentProfileImageUrl,
        long rank,
        Integer totalPoints) {
}
