package com.example.gradproj.EduNest.service.mylearning;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.mylearning.EnrolledMentorshipDto;
import com.example.gradproj.EduNest.dto.mylearning.MyLearningResponse;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.projections.ActiveMentorshipProgressProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MyLearningService {

    private final EnrollmentRepository enrollmentRepository;

    public MyLearningResponse getMyLearning(String email, int page, int size) {
        Page<ActiveMentorshipProgressProjection> result = enrollmentRepository
                .findAllEnrolledMentorshipsWithProgress(email, PageRequest.of(page, size));

        List<EnrolledMentorshipDto> content = result.getContent().stream().map(m -> {
            long totalItems = safe(m.getTotalTasks()) + safe(m.getTotalQuizzes()) + safe(m.getTotalProjects());
            long submittedItems = safe(m.getSubmittedTasks()) + safe(m.getSubmittedQuizzes()) + safe(m.getSubmittedProjects());
            int progress = totalItems > 0 ? (int) ((submittedItems * 100) / totalItems) : 0;

            return EnrolledMentorshipDto.builder()
                    .mentorshipId(m.getMentorshipId())
                    .title(m.getTitle())
                    .subtitle(m.getSubtitle())
                    .category(m.getCategory())
                    .difficultyLevel(m.getDifficultyLevel())
                    .coverImageUrl(m.getCoverImageUrl())
                    .totalPoints(m.getTotalPoints())
                    .progressPercentage(progress)
                    .totalTasks(m.getTotalTasks())
                    .submittedTasks(m.getSubmittedTasks())
                    .totalQuizzes(m.getTotalQuizzes())
                    .submittedQuizzes(m.getSubmittedQuizzes())
                    .totalProjects(m.getTotalProjects())
                    .submittedProjects(m.getSubmittedProjects())
                    .totalLectures(m.getTotalLectures())
                    .status(m.getStatus())
                    .build();
        }).toList();

        long completedMentorships = result.stream().filter(m -> "COMPLETED".equals(m.getStatus())).count();
        long totalPoints = result.stream().mapToLong(m -> m.getTotalPoints() != null ? m.getTotalPoints() : 0).sum();
        double averageProgress = content.isEmpty() ? 0 :
                content.stream().mapToInt(EnrolledMentorshipDto::getProgressPercentage).average().orElse(0);

        PageResponse<EnrolledMentorshipDto> pageResponse = PageResponse.<EnrolledMentorshipDto>builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();

        return MyLearningResponse.builder()
                .completedMentorships(completedMentorships)
                .averageProgress(Math.round(averageProgress * 100.0) / 100.0)
                .totalPoints(totalPoints)
                .mentorships(pageResponse)
                .build();
    }

    private long safe(Long val) {
        return val != null ? val : 0;
    }
}
