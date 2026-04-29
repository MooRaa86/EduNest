package com.example.gradproj.EduNest.service.admin;

import com.example.gradproj.EduNest.dto.admin.AdminDashboardSummaryResponse;
import com.example.gradproj.EduNest.dto.admin.AdminMentorDetailResponse;
import com.example.gradproj.EduNest.dto.admin.AdminStudentDetailResponse;
import com.example.gradproj.EduNest.dto.dashboard.UsersChartResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.repository.users.projection.MentorStatsProjection;
import com.example.gradproj.EduNest.repository.users.projection.MonthlyUsersProjection;
import com.example.gradproj.EduNest.repository.users.projection.StudentStatsProjection;
import com.example.gradproj.EduNest.repository.users.projection.UserListProjection;
import com.example.gradproj.EduNest.repository.users.projection.UserRoleProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminUserDirectory {

    private final UserRepository userRepository;
    private final MentorRepository mentorRepository;
    private final StudentRepository studentRepository;

    @Transactional(readOnly = true)
    public Object getUserById(Long id) {
        UserRoleProjection roleProjection = userRepository.findRoleByUserId(id)
                .orElseThrow(() -> new globalLogicEx("User not found"));

        String roleName = roleProjection.getRoleName();

        if ("MENTOR".equals(roleName)) {
            return buildMentorResponse(id);
        } else if ("STUDENT".equals(roleName)) {
            return buildStudentResponse(id);
        }

        return null;
    }

    private AdminMentorDetailResponse buildMentorResponse(Long id) {
        Mentor mentor = mentorRepository.findMentorWithSocialMediaById(id)
                .orElseThrow(() -> new globalLogicEx("Mentor not found"));

        MentorStatsProjection stats = mentorRepository.getMentorStats(id);

        List<AdminMentorDetailResponse.SocialMediaItem> socialMedia = mentor.getSocialMediaLinks().stream()
                .map(sm -> AdminMentorDetailResponse.SocialMediaItem.builder()
                        .name(sm.getName().name())
                        .url(sm.getUrl())
                        .build())
                .toList();

        List<AdminMentorDetailResponse.BadgeSummary> badgeSummaries = mentorRepository.getMentorBadges(id).stream()
                .map(badge -> AdminMentorDetailResponse.BadgeSummary.builder()
                        .id(badge.getId())
                        .title(badge.getTitle())
                        .category(badge.getCategory())
                        .points(badge.getPoints())
                        .build())
                .toList();

        return AdminMentorDetailResponse.builder()
                .id(mentor.getId())
                .firstName(mentor.getFirstName())
                .lastName(mentor.getLastName())
                .email(mentor.getEmail())
                .profileImageUrl(mentor.getProfileImageUrl())
                .enabled(mentor.isEnabled())
                .bio(mentor.getBio())
                .jobTitle(mentor.getJobTitle())
                .yearsOfExperience(mentor.getYearsOfExperience())
                .totalSessions(stats.getTotalSessions())
                .totalStudents(stats.getTotalStudents())
                .averageRating(stats.getAverageRating())
                .totalBadges(stats.getTotalBadges())
                .mentorshipCount(stats.getMentorshipCount())
                .socialMedia(socialMedia)
                .badges(badgeSummaries)
                .build();
    }

    private AdminStudentDetailResponse buildStudentResponse(Long id) {
        Student student = studentRepository.findStudentWithSocialMediaById(id)
                .orElseThrow(() -> new globalLogicEx("Student not found"));

        StudentStatsProjection stats = studentRepository.getStudentStats(id);

        List<AdminStudentDetailResponse.SocialMediaItem> socialMedia = student.getSocialMediaLinks().stream()
                .map(sm -> AdminStudentDetailResponse.SocialMediaItem.builder()
                        .name(sm.getName().name())
                        .url(sm.getUrl())
                        .build())
                .toList();

        List<AdminStudentDetailResponse.BadgeAwardSummary> badgeAwards = studentRepository.getStudentBadgeAwards(id).stream()
                .map(badge -> AdminStudentDetailResponse.BadgeAwardSummary.builder()
                        .id(badge.getId())
                        .badgeTitle(badge.getTitle())
                        .badgeCategory(badge.getCategory())
                        .points(badge.getPoints())
                        .build())
                .toList();

        return AdminStudentDetailResponse.builder()
                .id(student.getId())
                .firstName(student.getFirstName())
                .lastName(student.getLastName())
                .email(student.getEmail())
                .profileImageUrl(student.getProfileImageUrl())
                .enabled(student.isEnabled())
                .educationalLevel(student.getEducationalLevel() != null ? student.getEducationalLevel().name() : null)
                .jobTitle(student.getJobTitle())
                .bio(student.getBio())
                .totalEnrollments(stats.getTotalEnrollments())
                .totalCompletedMentorships(stats.getTotalCompletedMentorships())
                .totalBadgesEarned(stats.getTotalBadgesEarned())
                .socialMedia(socialMedia)
                .badgeAwards(badgeAwards)
                .build();
    }

    public List<UsersChartResponse> getMonthlyUsersForLastMonths(Integer months) {
        LocalDateTime startDate = null;
        if (months != null && months > 0) {
            startDate = LocalDateTime.now().minusMonths(months);
        }
        List<MonthlyUsersProjection> data = userRepository.getMonthlyUsersForLastPeriod(startDate);

        return data.stream()
                .map(p -> new UsersChartResponse(
                        Month.of(p.getMonth()).getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        p.getYear(),
                        p.getTotalUsers()
                ))
                .toList();
    }

    public PageResponse<UserListProjection> getAllUsersPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserListProjection> users = userRepository.findAllUsers(pageable);
        return buildPageResponse(users);
    }

    public PageResponse<UserListProjection> getUsersByRole(String roleName, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserListProjection> users = userRepository.findUsersByRoleName(roleName, pageable);
        return buildPageResponse(users);
    }

    private PageResponse<UserListProjection> buildPageResponse(Page<UserListProjection> page) {
        return PageResponse.<UserListProjection>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getAdminDashboardSummary(Integer months, int page, int size) {
        List<UsersChartResponse> monthlyUsers = getMonthlyUsersForLastMonths(months);
        PageResponse<UserListProjection> allUsersPaginated = getAllUsersPaginated(page, size);

        return AdminDashboardSummaryResponse.builder()
                .monthlyUsers(monthlyUsers)
                .allUsersPaginated(allUsersPaginated)
                .build();
    }
}
