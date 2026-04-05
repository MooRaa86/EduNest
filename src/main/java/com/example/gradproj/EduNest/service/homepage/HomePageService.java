package com.example.gradproj.EduNest.service.homepage;

import com.example.gradproj.EduNest.dto.homepage.ContinueLearningDto;
import com.example.gradproj.EduNest.dto.homepage.StudentHomePageResponse;
import com.example.gradproj.EduNest.dto.homepage.UpcomingItemDto;
import com.example.gradproj.EduNest.dto.homepage.studentProgressDto;
import com.example.gradproj.EduNest.dto.livesession.response.StudentUpcomingSessionResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.mentorShip.projections.ContinueLearningWithProgressProjection;
import com.example.gradproj.EduNest.repository.mentorShip.projections.RecommendedMentorshipProjection;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.projects.projection.UpcomingProjectProjection;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.projection.UpcomingTaskProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HomePageService {
    private final LiveSessionRepository sessionRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final MentorShipRepository mentorShipRepository;

    public studentProgressDto getStudentMentorshipProgress(String email, Long mentorshipId) {
        var progress = enrollmentRepository.getContentProgress(mentorshipId, email);

        long totalItems = (progress.getTotalTasks() != null ? progress.getTotalTasks() : 0) +
                (progress.getTotalQuizzes() != null ? progress.getTotalQuizzes() : 0) +
                (progress.getTotalProjects() != null ? progress.getTotalProjects() : 0);

        long completedItems = (progress.getCompletedTasks() != null ? progress.getCompletedTasks() : 0) +
                (progress.getCompletedQuizzes() != null ? progress.getCompletedQuizzes() : 0) +
                (progress.getCompletedProjects() != null ? progress.getCompletedProjects() : 0);

        int progressPercentage = totalItems > 0 ? (int)((completedItems * 100) / totalItems) : 0;

        return studentProgressDto.builder()
                .totalTasks(progress.getTotalTasks())
                .completedTasks(progress.getCompletedTasks())
                .totalQuizzes(progress.getTotalQuizzes())
                .completedQuizzes(progress.getCompletedQuizzes())
                .totalProjects(progress.getTotalProjects())
                .completedProjects(progress.getCompletedProjects())
                .progressPercentage(progressPercentage)
                .build();
    }

    public StudentHomePageResponse getStudentHomePage(String email) {
        LocalDateTime now = LocalDateTime.now();
        List<UpcomingItemDto> allItems = new ArrayList<>();

        List<StudentUpcomingSessionResponse> sessions = sessionRepository
                .findUpcomingSessionsByStudentEmail(email, now, PageRequest.of(0, 2))
                .getContent();
        sessions.forEach(s -> allItems.add(UpcomingItemDto.builder()
                .id(s.getSessionId())
                .title(s.getSessionName())
                .type("SESSION")
                .dueDate(s.getSessionStartDate())
                .mentorshipId(s.getMentorshipId())
                .mentorshipTitle(s.getMentorshipName())
                .weekId(s.getWeekId())
                .weekTitle(s.getWeekTitle())
                .build()));

        List<UpcomingTaskProjection> tasks = taskRepository.findUpcomingTasksByStudentEmail(
                email, now, PageRequest.of(0, 2));
        tasks.forEach(t -> allItems.add(UpcomingItemDto.builder()
                        .id(t.getId())
                        .title(t.getTitle())
                        .type("TASK")
                        .dueDate(t.getDueAt())
                        .mentorshipId(t.getMentorshipId())
                        .mentorshipTitle(t.getMentorshipTitle())
                        .weekId(t.getWeekId())
                        .weekTitle(t.getWeekTitle())
                        .points(t.getPoints())
                        .build()));

        List<UpcomingProjectProjection> projects = projectRepository.findUpcomingProjectsByStudentEmail(
                email, now, PageRequest.of(0, 2));
        projects.forEach(p -> allItems.add(UpcomingItemDto.builder()
                        .id(p.getId())
                        .title(p.getTitle())
                        .type("PROJECT")
                        .dueDate(p.getEndAt())
                        .mentorshipId(p.getMentorshipId())
                        .mentorshipTitle(p.getMentorshipTitle())
                        .weekId(p.getWeekId())
                        .weekTitle(p.getWeekTitle())
                        .points(p.getPoints())
                        .build()));

        allItems.sort(Comparator.comparing(UpcomingItemDto::getDueDate));

        List<ContinueLearningWithProgressProjection> mentorships = enrollmentRepository
                .findContinueLearningWithProgress(email, 2);

        List<ContinueLearningDto> continueLearning = mentorships.stream()
                .map(m -> {
                    long totalItems = (m.getTotalTasks() != null ? m.getTotalTasks() : 0) +
                            (m.getTotalQuizzes() != null ? m.getTotalQuizzes() : 0) +
                            (m.getTotalProjects() != null ? m.getTotalProjects() : 0);

                    long completedItems = (m.getCompletedTasks() != null ? m.getCompletedTasks() : 0) +
                            (m.getCompletedQuizzes() != null ? m.getCompletedQuizzes() : 0) +
                            (m.getCompletedProjects() != null ? m.getCompletedProjects() : 0);

                    int progressPercentage = totalItems > 0 ? (int)((completedItems * 100) / totalItems) : 0;

                    return ContinueLearningDto.builder()
                            .mentorshipId(m.getMentorshipId())
                            .title(m.getTitle())
                            .coverImageUrl(m.getCoverImageUrl())
                            .mentorName(m.getMentorName())
                            .progressPercentage(progressPercentage)
                            .completedItems((int)completedItems)
                            .totalItems((int)totalItems)
                            .build();
                })
                .toList();

        List<RecommendedMentorshipProjection> recommended = getRecommendedMentorships(email);

        return StudentHomePageResponse.builder()
                .upcomingItems(allItems)
                .continueLearning(continueLearning)
                .recommendedMentorships(recommended)
                .build();
    }

    public List<RecommendedMentorshipProjection> getRecommendedMentorships(String email) {
        List<String> categories = mentorShipRepository.findCategoriesByStudentEmail(email);

        categories = categories.stream()
                .map(String::toLowerCase)
                .toList();

        boolean hasCategories = !categories.isEmpty();
        
        if (!hasCategories) {
            categories = List.of("");
        }
        
        return mentorShipRepository.findRecommendedMentorships(email, categories, hasCategories);
    }

    public PageResponse<UpcomingItemDto> getUpcomingItemsByMentorship(String email, Long mentorshipId, int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        List<UpcomingItemDto> allItems = new ArrayList<>();

        List<StudentUpcomingSessionResponse> sessions = sessionRepository
                .findUpcomingSessionsByStudentEmailAndMentorship(email, mentorshipId, now);
        sessions.forEach(s -> allItems.add(UpcomingItemDto.builder()
                .id(s.getSessionId())
                .title(s.getSessionName())
                .type("SESSION")
                .dueDate(s.getSessionStartDate())
                .mentorshipId(s.getMentorshipId())
                .mentorshipTitle(s.getMentorshipName())
                .weekId(s.getWeekId())
                .weekTitle(s.getWeekTitle())
                .build()));

        List<UpcomingTaskProjection> tasks = taskRepository
                .findUpcomingTasksByStudentEmailAndMentorship(email, mentorshipId, now);
        tasks.forEach(t -> allItems.add(UpcomingItemDto.builder()
                .id(t.getId())
                .title(t.getTitle())
                .type("TASK")
                .dueDate(t.getDueAt())
                .mentorshipId(t.getMentorshipId())
                .mentorshipTitle(t.getMentorshipTitle())
                .weekId(t.getWeekId())
                .weekTitle(t.getWeekTitle())
                .points(t.getPoints())
                .build()));

        List<UpcomingProjectProjection> projects = projectRepository
                .findUpcomingProjectsByStudentEmailAndMentorship(email, mentorshipId, now);
        projects.forEach(p -> allItems.add(UpcomingItemDto.builder()
                .id(p.getId())
                .title(p.getTitle())
                .type("PROJECT")
                .dueDate(p.getEndAt())
                .mentorshipId(p.getMentorshipId())
                .mentorshipTitle(p.getMentorshipTitle())
                .weekId(p.getWeekId())
                .weekTitle(p.getWeekTitle())
                .points(p.getPoints())
                .build()));

        allItems.sort(Comparator.comparing(UpcomingItemDto::getDueDate));
        
        int start = page * size;
        int end = Math.min(start + size, allItems.size());
        List<UpcomingItemDto> pagedItems = start < allItems.size() ? allItems.subList(start, end) : List.of();
        
        return PageResponse.<UpcomingItemDto>builder()
                .content(pagedItems)
                .page(page)
                .size(size)
                .totalElements(allItems.size())
                .totalPages((int) Math.ceil((double) allItems.size() / size))
                .build();
    }

}
