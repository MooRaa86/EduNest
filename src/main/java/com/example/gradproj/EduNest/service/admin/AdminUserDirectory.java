package com.example.gradproj.EduNest.service.admin;

import com.example.gradproj.EduNest.dto.admin.AdminDashboardSummaryResponse;
import com.example.gradproj.EduNest.dto.admin.AdminMentorDetailResponse;
import com.example.gradproj.EduNest.dto.admin.AdminStudentDetailResponse;
import com.example.gradproj.EduNest.dto.dashboard.UsersChartResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.admin.UserAdminBadgeRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.repository.users.projection.*;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminUserDirectory {

    private final UserRepository userRepository;
    private final MentorRepository mentorRepository;
    private final StudentRepository studentRepository;
    private final UserAdminBadgeRepository userAdminBadgeRepository;

    @Transactional(readOnly = true)
    public Object getUserById(Long id) {
        Optional<Mentor> mentorOpt = mentorRepository.findMentorWithSocialMediaById(id);
        if (mentorOpt.isPresent()) {
            return buildMentorResponse(mentorOpt.get());
        }

        Optional<Student> studentOpt = studentRepository.findStudentWithSocialMediaById(id);
        if (studentOpt.isPresent()) {
            return buildStudentResponse(studentOpt.get());
        }

        throw new globalLogicEx("User not found");
    }

    private AdminMentorDetailResponse buildMentorResponse(Mentor mentor) {

        MentorStatsProjection stats = mentorRepository.getMentorStats(mentor.getId());

        List<AdminMentorDetailResponse.SocialMediaItem> socialMedia = mentor.getSocialMediaLinks().stream()
                .map(sm -> AdminMentorDetailResponse.SocialMediaItem.builder()
                        .name(sm.getName().name())
                        .url(sm.getUrl())
                        .build())
                .toList();

        List<AdminMentorDetailResponse.AdminBadgeSummary> adminBadgeSummaries = userAdminBadgeRepository.findAdminBadgesByUserId(mentor.getId()).stream()
                .map(badge -> AdminMentorDetailResponse.AdminBadgeSummary.builder()
                        .id(badge.getId())
                        .name(badge.getName())
                        .description(badge.getDescription())
                        .type(badge.getType())
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
                .totalBadges((long) adminBadgeSummaries.size())
                .mentorshipCount(stats.getMentorshipCount())
                .socialMedia(socialMedia)
                .adminBadges(adminBadgeSummaries)
                .build();
    }

    private AdminStudentDetailResponse buildStudentResponse(Student student) {

        StudentStatsProjection stats = studentRepository.getStudentStats(student.getId());

        List<AdminStudentDetailResponse.SocialMediaItem> socialMedia = student.getSocialMediaLinks().stream()
                .map(sm -> AdminStudentDetailResponse.SocialMediaItem.builder()
                        .name(sm.getName().name())
                        .url(sm.getUrl())
                        .build())
                .toList();

        List<AdminStudentDetailResponse.AdminBadgeSummary> adminBadgeSummaries = userAdminBadgeRepository.findAdminBadgesByUserId(student.getId()).stream()
                .map(badge -> AdminStudentDetailResponse.AdminBadgeSummary.builder()
                        .id(badge.getId())
                        .name(badge.getName())
                        .description(badge.getDescription())
                        .type(badge.getType())
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
                .totalBadgesEarned((long) adminBadgeSummaries.size())
                .socialMedia(socialMedia)
                .adminBadges(adminBadgeSummaries)
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
