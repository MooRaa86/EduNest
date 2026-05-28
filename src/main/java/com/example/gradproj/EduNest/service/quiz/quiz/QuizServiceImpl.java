package com.example.gradproj.EduNest.service.quiz.quiz;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
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

import java.util.List;

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


    @Override
    @Transactional
    public QuizResponseDTO createQuiz(QuizCreateDTO quizCreateDTO) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnWeek(quizCreateDTO.getWeekId(), email)) {
            throw new AccessDeniedException("You are not authorized to create quiz for this week");
        }

        Week week=weekRepository.findById(quizCreateDTO.getWeekId()).orElseThrow(
                ()->new globalLogicEx("Week not found")
        );


        Quiz quiz = Quiz.builder()
                .title(quizCreateDTO.getTitle())
                .durationMinutes(quizCreateDTO.getDurationMinutes())
                .description(quizCreateDTO.getDescription())
                .week(week)
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
                .id(quiz.getId())
                .title(quiz.getTitle())
                .durationMinutes(quiz.getDurationMinutes())
                .description(quiz.getDescription())
                .status(quiz.getStatus())
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

        if (!quizRepository.existsById(id)) {
         throw  new globalLogicEx("Quiz not found");
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

        if (quiz.getStatus() == QuizStatus.PUBLISHED || quiz.getStatus() == QuizStatus.CLOSED) {
            throw new globalLogicEx("Cannot update published or closed quiz");
        }

        QuizStatus oldStatus = quiz.getStatus();
        if (quizUpdateDto.getTitle() != null) quiz.setTitle(quizUpdateDto.getTitle());
        if (quizUpdateDto.getDescription() != null) quiz.setDescription(quizUpdateDto.getDescription());
        if (quizUpdateDto.getDurationMinutes() != null) quiz.setDurationMinutes(quizUpdateDto.getDurationMinutes());
        if (quizUpdateDto.getStatus() != null) quiz.setStatus(quizUpdateDto.getStatus());


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
                .id(quiz.getId())
                .title(quiz.getTitle())
                .durationMinutes(quiz.getDurationMinutes())
                .description(quiz.getDescription())
                .status(quiz.getStatus())
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
                .id(quiz.getId())
                .title(quiz.getTitle())
                .durationMinutes(quiz.getDurationMinutes())
                .description(quiz.getDescription())
                .status(quiz.getStatus())
                .submissions(quiz.getSubmissions() != null ? quiz.getSubmissions().size() : 0)
                .averageScore(calculateAverageScore(quiz))
                .build();
    }

    @Override
    public PageResponse<QuizResponseDTO> getQuizzes(String quizName, QuizStatus status,Long msid, Pageable pageable) {
        String email = securityService.getCurrentUserEmail();
        boolean isMentor = securityService.isMentorOwnMentorship(msid, email);

        if (!isMentor) {
            throw new AccessDeniedException("You are not authorized to view quizzes for this mentorship");
        }

        Page<Quiz> quizzes = quizRepository.findQuizzesByMentorship(msid,quizName, status,pageable);

        List<QuizResponseDTO> quizDTOs = quizzes.getContent().stream()
                .map(quiz -> QuizResponseDTO.builder()
                        .id(quiz.getId())
                        .title(quiz.getTitle())
                        .durationMinutes(quiz.getDurationMinutes())
                        .description(quiz.getDescription())
                        .status(quiz.getStatus())
                        .submissions(quiz.getSubmissions() != null ? quiz.getSubmissions().size() : 0)
                        .averageScore(calculateAverageScore(quiz))
                        .build())
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

        Page<Quiz> quizzes = quizRepository.findStudentQuizzesByMentorship(msid, quizName, status, pageable);

        List<QuizResponseDTO> quizDTOs = quizzes.getContent().stream()
                .map(quiz -> QuizResponseDTO.builder()
                        .id(quiz.getId())
                        .title(quiz.getTitle())
                        .durationMinutes(quiz.getDurationMinutes())
                        .description(quiz.getDescription())
                        .status(quiz.getStatus())
                        .submissions(quiz.getSubmissions() != null ? quiz.getSubmissions().size() : 0)
                        .averageScore(calculateAverageScore(quiz))
                        .build())
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

        if (!(mentorshipRepository.existsById(mentorShipId)))
        {
            throw  new globalLogicEx("MentorShip not found");
        }
        List<Quiz> allQuizzes = quizRepository.findByWeek_Mentorship_Id(mentorShipId);

        int totalQuizzes = allQuizzes.size();
        int publishedCount = 0;
        int draftCount = 0;
        double sumAverageScores = 0.0;

        for (Quiz quiz : allQuizzes) {
            publishedCount += (quiz.getStatus() == QuizStatus.PUBLISHED ? 1 : 0);
            draftCount += (quiz.getStatus() == QuizStatus.DRAFT ? 1 : 0);
            sumAverageScores += calculateAverageScore(quiz);
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

        if (!(mentorshipRepository.existsById(mentorShipId)))
        {
            throw  new globalLogicEx("MentorShip not found");
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<Quiz>allQuizzes=quizRepository.findByWeek_Mentorship_Id(mentorShipId,pageable);
        List<QuizOverviewDto>quizOverviewDtos=allQuizzes.stream()
                .map(this::mapToQuizOverviewDto)
                .toList();

        PageResponse<QuizOverviewDto>pageResponse= PageResponse.<QuizOverviewDto>builder()
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
    public QuizOverviewResponseDto getQuizOverviewDto(Long quizId,int page,int size) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnQuiz(quizId, email)) {
            throw new AccessDeniedException("You are not authorized to view overview for this quiz");
        }

        QuizStatisticsDTO quizStatisticsDTO=getQuizStatistics(quizId);
        List<QuizSubmissionResponseDTO> submissions=quizSubmissionService.getAllSubmissionsByQuiz(quizId,page,size);
         double fullMark=quizStatisticsDTO.getTotalPoints();
         for (QuizSubmissionResponseDTO submission : submissions) {
             if (submission.getScore() == null) {
                 submission.setStatus("Not Submitted");
             }
             else if (submission.getScore() >= (fullMark / 2)) {
                 submission.setStatus("Passed");
             }
             else {
                 submission.setStatus("Failed");
             }
         }

        return QuizOverviewResponseDto.builder()
                .quizStatistics(quizStatisticsDTO)
                .submissions(submissions)
                .build();
    }
    @Override
    public void changeStatus(Long quizId, QuizStatus quizStatus) {
        String email = securityService.getCurrentUserEmail();
        if (!securityService.isMentorOwnQuiz(quizId, email)) {
            throw new AccessDeniedException("You are not authorized to change status of this quiz");
        }

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        if (quizStatus == QuizStatus.CLOSED && quiz.getStatus() != QuizStatus.PUBLISHED) {
            throw new globalLogicEx("Only published quizzes can be closed");
        }

        if (quizStatus == QuizStatus.PUBLISHED && quiz.getStatus() == QuizStatus.PUBLISHED) {
            throw new globalLogicEx("Quiz is already published");
        }

        if(quizStatus==QuizStatus.CLOSED) {
            notificationService.sendToMentorshipStudents(
                    quiz.getWeek().getMentorship().getId(),
                    "Quiz Closed",
                    "A Quiz " + quiz.getTitle() + " has been closed in week " + quiz.getWeek().getTitle() + " in mentorship " + quiz.getWeek().getMentorship().getTitle(),
                    NotificationType.QUIZ
            );        }

        if (quizStatus==QuizStatus.PUBLISHED){
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

    private double calculateAverageScore(Quiz quiz) {

        if (quiz.getSubmissions() == null || quiz.getSubmissions().isEmpty()) {
            return 0;
        }

        double average = quiz.getSubmissions().stream()
                .mapToDouble(s -> s.getScore() != null ? s.getScore() : 0)
                .average()
                .orElse(0.0);

        return Math.round(average * 100.0) / 100.0;
    }
    private QuizOverviewDto mapToQuizOverviewDto(Quiz quiz) {
        return QuizOverviewDto.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .averageScore(calculateAverageScore(quiz))
                .status(quiz.getStatus())
                .submissions(quiz.getSubmissions().size())
                .build();
    }
}
