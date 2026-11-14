package com.example.gradproj.EduNest.service.quiz.quiz;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.utils.PaginationPolicy;
import com.example.gradproj.EduNest.dto.quiz.request.QuizCreateDTO;
import com.example.gradproj.EduNest.dto.quiz.request.QuizDashboardDTO;
import com.example.gradproj.EduNest.dto.quiz.request.QuizStatisticsDTO;
import com.example.gradproj.EduNest.dto.quiz.request.QuizUpdateDto;
import com.example.gradproj.EduNest.dto.quiz.response.*;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.quiz.Question;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.security.SecurityService;
import com.example.gradproj.EduNest.service.quiz.submission.QuizSubmissionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private final QuizRepository quizRepository;
    private final MentorShipRepository mentorshipRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final WeekRepository weekRepository;
    private final QuizSubmissionService quizSubmissionService;
    private final NotificationService notificationService;
    private final SecurityService securityService;
    private final QuizSubmissionRepository quizSubmissionRepository;


    @Override
    @Transactional
    public QuizResponseDTO createQuiz(QuizCreateDTO quizCreateDTO) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnWeek(quizCreateDTO.getWeekId(), email)) {
            throw new AccessDeniedException("You are not authorized to create quiz for this week");
        }

        Week week = weekRepository.findByPublicId(quizCreateDTO.getWeekId()).orElseThrow(
                () -> new globalLogicEx("Week not found")
        );

        if (quizCreateDTO.getStatus() == QuizStatus.PUBLISHED) {
            // EB-53 / D-08 — a quiz published at creation time must carry a valid closeAt.
            requireValidCloseAt(quizCreateDTO.getCloseAt());
        }

        Quiz quiz = Quiz.builder()
                .title(quizCreateDTO.getTitle())
                .durationMinutes(quizCreateDTO.getDurationMinutes())
                .description(quizCreateDTO.getDescription())
                .week(week)
                .closeAt(quizCreateDTO.getCloseAt())
                .status(quizCreateDTO.getStatus() != null ? quizCreateDTO.getStatus() : QuizStatus.DRAFT)
                .build();

        quiz = quizRepository.save(quiz);

        if (quiz.getStatus() == QuizStatus.PUBLISHED) {
            notificationService.sendToMentorshipStudents(
                    week.getMentorship().getId(),
                    "New Quiz",
                    "A new Quiz " + quiz.getTitle() + " has been created in week " + week.getTitle() + " in mentorship " + week.getMentorship().getTitle(),
                    NotificationType.QUIZ
            );
        }

        return QuizResponseDTO.builder()
                .id(quiz.getPublicId())
                .title(quiz.getTitle())
                .durationMinutes(quiz.getDurationMinutes())
                .description(quiz.getDescription())
                .status(quiz.getStatus())
                .closeAt(quiz.getCloseAt())
                .submissions(0)
                .averageScore(0.0)
                .build();
    }

    @Override
    @Transactional
    public void deleteQuiz(Long id) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnQuiz(id, email)) {
            throw new AccessDeniedException("You are not authorized to delete this quiz");
        }

        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        // EB-51 — L1 (week) -> L2 (quiz) shared gate: serialised against quiz-submit and cascade deletes
        weekRepository.findForUpdateById(quiz.getWeek().getId())
                .orElseThrow(() -> new globalLogicEx("Week not found"));
        quizRepository.findForUpdateById(id)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        if (quizSubmissionRepository.existsByQuiz_IdAndScoreIsNotNull(id)) {
            throw new globalLogicEx("Cannot delete a quiz that has scored submissions");
        }

        quizRepository.deleteById(id);
    }


    @Override
    @Transactional
    public QuizResponseDTO updateQuiz(Long id, QuizUpdateDto quizUpdateDto) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnQuiz(id, email)) {
            throw new AccessDeniedException("You are not authorized to update this quiz");
        }

        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        // EB-51 — L1 (week) -> L2 (quiz) shared gate
        weekRepository.findForUpdateById(quiz.getWeek().getId())
                .orElseThrow(() -> new globalLogicEx("Week not found"));
        quizRepository.findForUpdateById(id)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        if (quiz.getStatus() == QuizStatus.PUBLISHED || quiz.getStatus() == QuizStatus.CLOSED) {
            throw new globalLogicEx("Cannot update published or closed quiz");
        }

        QuizStatus oldStatus = quiz.getStatus();
        if (quizUpdateDto.getTitle() != null) quiz.setTitle(quizUpdateDto.getTitle());
        if (quizUpdateDto.getDescription() != null) quiz.setDescription(quizUpdateDto.getDescription());
        if (quizUpdateDto.getDurationMinutes() != null) quiz.setDurationMinutes(quizUpdateDto.getDurationMinutes());
        if (quizUpdateDto.getCloseAt() != null) quiz.setCloseAt(quizUpdateDto.getCloseAt());
        if (quizUpdateDto.getStatus() != null) quiz.setStatus(quizUpdateDto.getStatus());

        if (oldStatus != QuizStatus.PUBLISHED && quiz.getStatus() == QuizStatus.PUBLISHED) {
            // EB-53 / D-08 — publishing through the update endpoint requires a valid closeAt.
            requireValidCloseAt(quiz.getCloseAt());
        }


        quiz = quizRepository.save(quiz);

        if (oldStatus != QuizStatus.PUBLISHED && quiz.getStatus() == QuizStatus.PUBLISHED) {
            notificationService.sendToMentorshipStudents(
                    quiz.getWeek().getMentorship().getId(),
                    "New Quiz",
                    "A new Quiz " + quiz.getTitle() + " has been published in week " + quiz.getWeek().getTitle() + " in mentorship " + quiz.getWeek().getMentorship().getTitle()
                            + " it will be closed in " + quiz.getDurationMinutes() + " minutes",
                    NotificationType.QUIZ
            );
        }

        return QuizResponseDTO.builder()
                .id(quiz.getPublicId())
                .title(quiz.getTitle())
                .durationMinutes(quiz.getDurationMinutes())
                .description(quiz.getDescription())
                .status(quiz.getStatus())
                .closeAt(quiz.getCloseAt())
                .submissions(quiz.getSubmissions() != null ? quiz.getSubmissions().size() : 0)
                .averageScore(calculateAverageScore(quiz))
                .build();
    }

    @Override
    public QuizResponseDTO getQuizDetails(Long id) {
        String email = securityService.getCurrentUserEmail();
        boolean isMentor = securityService.isMentorOwnQuiz(id, email);
        boolean isStudent = securityService.isStudentEnrolledByQuizId(email, id);

        if (!isMentor && !isStudent) {
            throw new AccessDeniedException("You are not authorized to access this quiz");
        }

        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        if (isStudent && quiz.getStatus() == QuizStatus.DRAFT) {
            throw new AccessDeniedException("Quiz is not published");
        }

        return QuizResponseDTO.builder()
                .id(quiz.getPublicId())
                .title(quiz.getTitle())
                .durationMinutes(quiz.getDurationMinutes())
                .description(quiz.getDescription())
                .status(quiz.getStatus())
                .closeAt(quiz.getCloseAt())
                .submissions(quiz.getSubmissions() != null ? quiz.getSubmissions().size() : 0)
                .averageScore(calculateAverageScore(quiz))
                .totalPoints(quiz.getQuestions().stream().mapToInt(Question::getPoints).sum())
                .build();
    }

    @Override
    public PageResponse<QuizResponseDTO> getQuizzes(String quizName, QuizStatus status, Long msid, Pageable pageable) {
        String email = securityService.getCurrentUserEmail();
        boolean isMentor = securityService.isMentorOwnMentorship(msid, email);

        if (!isMentor) {
            throw new AccessDeniedException("You are not authorized to view quizzes for this mentorship");
        }

        // EB-60 / P4-11 — F2: projection rows instead of entity rows; the
        // previous query fetch-joined the LAZY submissions bag under a Page,
        // which forced Hibernate into in-memory pagination (HHH000104) and
        // the mapping loop then navigated each quiz's submissions.
        Page<QuizRepository.QuizListRow> quizzes = quizRepository.findQuizzesByMentorship(msid, quizName, status, pageable);

        List<QuizResponseDTO> quizDTOs = quizzes.getContent().stream()
                .map(this::toQuizResponseDto)
                .toList();


        return PageResponse.<QuizResponseDTO>builder()
                .content(quizDTOs)
                .page(quizzes.getNumber())
                .size(quizzes.getSize())
                .totalElements(quizzes.getTotalElements())
                .totalPages(quizzes.getTotalPages())
                .build();
    }

    @Override
    public PageResponse<QuizResponseDTO> getStudentQuizzes(String quizName, QuizStatus status, Long msid, Pageable pageable) {
        String email = securityService.getCurrentUserEmail();
        boolean isStudent = enrollmentRepository.existsByMentorShip_IdAndStudent_Email(msid, email);

        if (!isStudent) {
            throw new AccessDeniedException("You are not enrolled in this mentorship");
        }

        // EB-60 / P4-11 — F2: same projection shape as the mentor list (see
        // getQuizzes); DRAFT exclusion preserved by the repository query.
        Page<QuizRepository.QuizListRow> quizzes = quizRepository.findStudentQuizzesByMentorship(msid, quizName, status, pageable);

        List<QuizResponseDTO> quizDTOs = quizzes.getContent().stream()
                .map(this::toQuizResponseDto)
                .toList();

        return PageResponse.<QuizResponseDTO>builder()
                .content(quizDTOs)
                .page(quizzes.getNumber())
                .size(quizzes.getSize())
                .totalElements(quizzes.getTotalElements())
                .totalPages(quizzes.getTotalPages())
                .build();
    }


    @Override
    public QuizDashboardDTO getQuizDashboard(Long mentorShipId) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnMentorship(mentorShipId, email)) {
            throw new AccessDeniedException("You are not authorized to view dashboard for this mentorship");
        }

        if (!(mentorshipRepository.existsById(mentorShipId))) {
            throw new globalLogicEx("MentorShip not found");
        }
        // EB-60 / P4-11 — F1: the loop used to load every quiz of the
        // mentorship and navigate its LAZY submissions collection (1 + N
        // queries, growing with quiz count). One grouped aggregate query now
        // supplies per-quiz status + average; the semantics are preserved
        // exactly: per-quiz average rounded to 2 decimals (calculateAverageScore),
        // summed over ALL quizzes (DRAFT/empty included), divided by the total
        // count, final value not re-rounded.
        List<QuizRepository.QuizAggregateRow> aggregates =
                quizRepository.findDashboardAggregatesByMentorship(mentorShipId);

        int totalQuizzes = aggregates.size();
        int publishedCount = 0;
        int draftCount = 0;
        double sumAverageScores = 0.0;

        for (QuizRepository.QuizAggregateRow row : aggregates) {
            publishedCount += (row.getStatus() == QuizStatus.PUBLISHED ? 1 : 0);
            draftCount += (row.getStatus() == QuizStatus.DRAFT ? 1 : 0);
            Double average = row.getAverageScore();
            if (average != null) {
                sumAverageScores += Math.round(average * 100.0) / 100.0;
            }
        }
        double averageScore = totalQuizzes > 0 ? sumAverageScores / totalQuizzes : 0.0;

        return QuizDashboardDTO.builder()
                .totalQuizzes(totalQuizzes)
                .publishedCount(publishedCount)
                .draftCount(draftCount)
                .averageScore(averageScore)
                .build();
    }

    @Override
    public QuizStatisticsDTO getQuizStatistics(Long quizId) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnQuiz(quizId, email)) {
            throw new AccessDeniedException("You are not authorized to view statistics for this quiz");
        }

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));


        List<Question> questions = quiz.getQuestions() != null ? quiz.getQuestions() : List.of();
        int totalQuestions = questions.size();

        int totalPoints = questions.stream().map(Question::getPoints)
                .reduce(0, Integer::sum);

        return QuizStatisticsDTO.builder()
                .quizTitle(quiz.getTitle())
                .description(quiz.getDescription())
                .status(quiz.getStatus())
                .averageScore(calculateAverageScore(quiz))
                .totalStudents(quiz.getWeek().getMentorship() != null ? enrollmentRepository.countByMentorShip(quiz.getWeek().getMentorship()) : 0)
                .totalSubmissions(quiz.getSubmissions() != null ? quiz.getSubmissions().size() : 0)
                .totalPoints(totalPoints)
                .totalQuestions(totalQuestions)
                .build();

    }

    @Override
    public MentorshipQuizzesOverviewResponseDto getMentorshipQuizzesOverview(Long mentorShipId, int page, int size) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnMentorship(mentorShipId, email)) {
            throw new AccessDeniedException("You are not authorized to view quizzes overview for this mentorship");
        }

        if (!(mentorshipRepository.existsById(mentorShipId))) {
            throw new globalLogicEx("MentorShip not found");
        }
        Pageable pageable = PaginationPolicy.ofClamped(page, size);
        // EB-60 / P4-11 — F2: projection rows (see getQuizzes); mentor overview
        // keeps DRAFT quizzes visible (no status filter), like the old query.
        Page<QuizRepository.QuizListRow> allQuizzes =
                quizRepository.findQuizzesByMentorship(mentorShipId, null, null, pageable);
        List<QuizOverviewDto> quizOverviewDtos = allQuizzes.stream()
                .map(this::toQuizOverviewDto)
                .toList();

        PageResponse<QuizOverviewDto> pageResponse = PageResponse.<QuizOverviewDto>builder()
                .content(quizOverviewDtos)
                .page(page)
                .size(size)
                .totalElements(allQuizzes.getTotalElements())
                .build();

        return MentorshipQuizzesOverviewResponseDto
                .builder()
                .quizDashboardDTO(getQuizDashboard(mentorShipId))
                .quizOverviewDtoPageResponse(pageResponse)
                .build();
    }

    @Override
    public QuizOverviewResponseDto getQuizOverviewDto(Long quizId, int page, int size) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnQuiz(quizId, email)) {
            throw new AccessDeniedException("You are not authorized to view overview for this quiz");
        }

        QuizStatisticsDTO quizStatisticsDTO = getQuizStatistics(quizId);
        List<QuizSubmissionResponseDTO> submissions = quizSubmissionService.getAllSubmissionsByQuiz(quizId, page, size);
        double fullMark = quizStatisticsDTO.getTotalPoints();
        for (QuizSubmissionResponseDTO submission : submissions) {
            if (submission.getScore() == null) {
                submission.setStatus("Not Submitted");
            } else if (submission.getScore() >= (fullMark / 2)) {
                submission.setStatus("Passed");
            } else {
                submission.setStatus("Failed");
            }
        }

        return QuizOverviewResponseDto.builder()
                .quizStatistics(quizStatisticsDTO)
                .submissions(submissions)
                .build();
    }

    @Override
    @Transactional
    public void changeStatus(Long quizId, QuizStatus quizStatus) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnQuiz(quizId, email)) {
            throw new AccessDeniedException("You are not authorized to change status of this quiz");
        }

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        // EB-51 — L1 (week) -> L2 (quiz) shared gate: serialize status transitions against concurrent submissions
        weekRepository.findForUpdateById(quiz.getWeek().getId())
                .orElseThrow(() -> new globalLogicEx("Week not found"));
        quiz = quizRepository.findForUpdateById(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        if (quiz.getStatus() == QuizStatus.CLOSED) {
            throw new globalLogicEx("Cannot change status of a closed quiz");
        }

        if (quizStatus == QuizStatus.CLOSED && quiz.getStatus() != QuizStatus.PUBLISHED) {
            throw new globalLogicEx("Only published quizzes can be closed");
        }

        if (quizStatus == QuizStatus.PUBLISHED && quiz.getStatus() == QuizStatus.PUBLISHED) {
            throw new globalLogicEx("Quiz is already published");
        }

        if (quizStatus == QuizStatus.DRAFT && (quizSubmissionRepository.existsByQuiz_Id(quizId)
                || quizSubmissionRepository.existsByQuiz_IdAndScoreIsNotNull(quizId))) {
            throw new globalLogicEx("Cannot revert quiz with submissions to draft");
        }

        if (quizStatus == QuizStatus.PUBLISHED && (quiz.getQuestions() == null || quiz.getQuestions().isEmpty())) {
            throw new globalLogicEx("Cannot publish a quiz with no questions");
        }

        if (quizStatus == QuizStatus.PUBLISHED) {
            // EB-53 / D-08 — publishing requires a valid closeAt. Legacy PUBLISHED
            // quizzes with closeAt == null keep their manual-close-only behavior.
            requireValidCloseAt(quiz.getCloseAt());
        }

        if (quizStatus == QuizStatus.CLOSED) {
            notificationService.sendToMentorshipStudents(
                    quiz.getWeek().getMentorship().getId(),
                    "Quiz Closed",
                    "A Quiz " + quiz.getTitle() + " has been closed in week " + quiz.getWeek().getTitle() + " in mentorship " + quiz.getWeek().getMentorship().getTitle(),
                    NotificationType.QUIZ
            );
        }

        if (quizStatus == QuizStatus.PUBLISHED) {
            notificationService.sendToMentorshipStudents(
                    quiz.getWeek().getMentorship().getId(),
                    "New Quiz",
                    "A new Quiz " + quiz.getTitle() + " has been published in week " + quiz.getWeek().getTitle() + " in mentorship " + quiz.getWeek().getMentorship().getTitle()
                            + " it will be closed in " + quiz.getDurationMinutes() + " minutes",
                    NotificationType.QUIZ
            );
        }
        quiz.setStatus(quizStatus);
        quizRepository.save(quiz);
    }


    /**
     * EB-53 / D-08 — a valid closeAt is non-null and in the future at publish
     * time; the attempt window (durationMinutes) and grace still govern each
     * individual submission inside that deadline.
     */
    private void requireValidCloseAt(LocalDateTime closeAt) {
        if (closeAt == null) {
            throw new globalLogicEx("closeAt is required when publishing a quiz");
        }
        if (!closeAt.isAfter(LocalDateTime.now())) {
            throw new globalLogicEx("closeAt must be in the future when publishing a quiz");
        }
    }

    private double calculateAverageScore(Quiz quiz) {

        if (quiz.getSubmissions() == null || quiz.getSubmissions().isEmpty()) {
            return 0;
        }

        double average = quiz.getSubmissions().stream()
                .filter(s -> s.getScore() != null)
                .mapToDouble(s -> s.getScore())
                .average()
                .orElse(0.0);

        return Math.round(average * 100.0) / 100.0;
    }

    // EB-60 / P4-11 — F2: projection mappers. COUNT/AVG come pre-aggregated
    // from the database; per-quiz average rounding mirrors
    // calculateAverageScore (2 decimals) so responses are byte-identical.
    private QuizResponseDTO toQuizResponseDto(QuizRepository.QuizListRow row) {
        Long submissionCount = row.getSubmissionCount();
        Double average = row.getAverageScore();
        return QuizResponseDTO.builder()
                .id(row.getPublicId())
                .title(row.getTitle())
                .durationMinutes(row.getDurationMinutes())
                .description(row.getDescription())
                .status(row.getStatus())
                .closeAt(row.getCloseAt())
                .submissions(submissionCount == null ? 0 : submissionCount.intValue())
                .averageScore(average == null ? 0.0 : Math.round(average * 100.0) / 100.0)
                .build();
    }

    private QuizOverviewDto toQuizOverviewDto(QuizRepository.QuizListRow row) {
        Long submissionCount = row.getSubmissionCount();
        Double average = row.getAverageScore();
        return QuizOverviewDto.builder()
                .id(row.getPublicId())
                .title(row.getTitle())
                .status(row.getStatus())
                .submissions(submissionCount == null ? 0 : submissionCount.intValue())
                .averageScore(average == null ? 0.0 : Math.round(average * 100.0) / 100.0)
                .build();
    }

    @Override
    @Transactional
    public void deleteQuiz(UUID id) {
        Long internalId = quizRepository.findIdByPublicId(id)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));
        deleteQuiz(internalId);
    }

    @Override
    @Transactional
    public QuizResponseDTO updateQuiz(UUID id, QuizUpdateDto quizUpdateDto) {
        Long internalId = quizRepository.findIdByPublicId(id)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));
        return updateQuiz(internalId, quizUpdateDto);
    }

    @Override
    public QuizResponseDTO getQuizDetails(UUID id) {
        Long internalId = quizRepository.findIdByPublicId(id)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));
        return getQuizDetails(internalId);
    }

    @Override
    public PageResponse<QuizResponseDTO> getQuizzes(String quizName, QuizStatus status, UUID msid, Pageable pageable) {
        Long internalMsid = mentorshipRepository.findIdByPublicId(msid)
                .orElseThrow(() -> new globalLogicEx("MentorShip not found"));
        return getQuizzes(quizName, status, internalMsid, pageable);
    }

    @Override
    public PageResponse<QuizResponseDTO> getStudentQuizzes(String quizName, QuizStatus status, UUID msid, Pageable pageable) {
        Long internalMsid = mentorshipRepository.findIdByPublicId(msid)
                .orElseThrow(() -> new globalLogicEx("MentorShip not found"));
        return getStudentQuizzes(quizName, status, internalMsid, pageable);
    }

    @Override
    public QuizDashboardDTO getQuizDashboard(UUID mentorshipID) {
        Long internalMsid = mentorshipRepository.findIdByPublicId(mentorshipID)
                .orElseThrow(() -> new globalLogicEx("MentorShip not found"));
        return getQuizDashboard(internalMsid);
    }

    @Override
    public QuizStatisticsDTO getQuizStatistics(UUID quizId) {
        Long internalId = quizRepository.findIdByPublicId(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));
        return getQuizStatistics(internalId);
    }

    @Override
    public QuizOverviewResponseDto getQuizOverviewDto(UUID quizId, int page, int size) {
        Long internalId = quizRepository.findIdByPublicId(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));
        return getQuizOverviewDto(internalId, page, size);
    }

    @Override
    @Transactional
    public void changeStatus(UUID quizId, QuizStatus quizStatus) {
        Long internalId = quizRepository.findIdByPublicId(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));
        changeStatus(internalId, quizStatus);
    }

    @Override
    public MentorshipQuizzesOverviewResponseDto getMentorshipQuizzesOverview(UUID mentorShipId, int page, int size) {
        Long internalMsid = mentorshipRepository.findIdByPublicId(mentorShipId)
                .orElseThrow(() -> new globalLogicEx("MentorShip not found"));
        return getMentorshipQuizzesOverview(internalMsid, page, size);
    }
}
