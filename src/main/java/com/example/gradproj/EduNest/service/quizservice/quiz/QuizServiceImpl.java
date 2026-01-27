package com.example.gradproj.EduNest.service.quizservice.quiz;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizCreateDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizDashboardDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizStatisticsDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizUpdateDto;
import com.example.gradproj.EduNest.dto.quizdto.response.QuizResponseDTO;
import com.example.gradproj.EduNest.entity.mentorship.mentorShipE;
import com.example.gradproj.EduNest.entity.quizentity.Question;
import com.example.gradproj.EduNest.entity.quizentity.Quiz;
import com.example.gradproj.EduNest.enums.quiz.QuizStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.mentorShipRepository;
import com.example.gradproj.EduNest.repository.quizrepository.QuizRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private final QuizRepository quizRepository;
    private final mentorShipRepository mentorshipRepository;


    @Override
    @Transactional
    public QuizResponseDTO createQuiz(QuizCreateDTO quizCreateDTO) {

        mentorShipE mentorship = mentorshipRepository.findById(quizCreateDTO.getMentorshipId())
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));


        Quiz quiz = Quiz.builder()
                .title(quizCreateDTO.getTitle())
                .durationMinutes(quizCreateDTO.getDurationMinutes())
                .description(quizCreateDTO.getDescription())
                .mentorship(mentorship)
                .status(quizCreateDTO.getStatus() != null ? quizCreateDTO.getStatus() : QuizStatus.DRAFT)
                .build();

        quiz = quizRepository.save(quiz);

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
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        quizRepository.delete(quiz);
    }


    @Override
    @Transactional
    public QuizResponseDTO updateQuiz(Long id, QuizUpdateDto quizUpdateDto) {
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        if (quizUpdateDto.getTitle() != null) quiz.setTitle(quizUpdateDto.getTitle());
        if (quizUpdateDto.getDescription() != null) quiz.setDescription(quizUpdateDto.getDescription());
        if (quizUpdateDto.getDurationMinutes() != null) quiz.setDurationMinutes(quizUpdateDto.getDurationMinutes());
//        if (quizUpdateDto.getMentorshipId() != null) {
//            mentorShipE mentorship = mentorshipRepository.findById(quizUpdateDto.getMentorshipId())
//                    .orElseThrow(() -> new globalLogicEx("Mentorship not found"));
//            quiz.setMentorship(mentorship);
//        }
        if (quizUpdateDto.getStatus() != null) quiz.setStatus(quizUpdateDto.getStatus());


        quiz = quizRepository.save(quiz);

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
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        return QuizResponseDTO.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .durationMinutes(quiz.getDurationMinutes())
                .status(quiz.getStatus())
                .submissions(quiz.getSubmissions() != null ? quiz.getSubmissions().size() : 0)
                .averageScore(calculateAverageScore(quiz))
                .build();
    }

    @Override
    public PageResponse<QuizResponseDTO> getQuizzes(String quizName, QuizStatus status, Pageable pageable) {
        Page<Quiz> quizzes = quizRepository.findQuizzes(quizName, status, pageable);

        List<QuizResponseDTO> quizDTOs = quizzes.getContent().stream()
                .map(quiz -> QuizResponseDTO.builder()
                        .id(quiz.getId())
                        .title(quiz.getTitle())
                        .durationMinutes(quiz.getDurationMinutes())
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

        mentorShipE mentorShip = mentorshipRepository.findById(mentorShipId)
                .orElseThrow(() -> new globalLogicEx("MentorShip not found"));
        List<Quiz> allQuizzes = quizRepository.findByMentorship_Id(mentorShipId);

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
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));


        List<Question> questions = quiz.getQuestions() != null ? quiz.getQuestions() : List.of();
        int totalQuestions = questions.size();

        int totalPoints = questions.stream().map(Question::getPoints)
                .reduce(0, Integer::sum);

        return QuizStatisticsDTO.builder()
                .status(quiz.getStatus())
                .averageScore(calculateAverageScore(quiz))
                .totalStudents(quiz.getMentorship() != null ? quiz.getMentorship().getStudents().size() : 0)
                .totalSubmissions(quiz.getSubmissions() != null ? quiz.getSubmissions().size() : 0)
                .totalPoints(totalPoints)
                .totalQuestions(totalQuestions)
                .build();

    }

    @Override
    public void changeStatus(Long quizId, QuizStatus quizStatus) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        if (quizStatus == QuizStatus.CLOSED && quiz.getStatus() != QuizStatus.PUBLISHED) {
            throw new globalLogicEx("Only published quizzes can be closed");
        }

        if (quizStatus == QuizStatus.PUBLISHED && quiz.getStatus() == QuizStatus.PUBLISHED) {
            throw new globalLogicEx("Quiz is already published");
        }

        quiz.setStatus(quizStatus);
        quizRepository.save(quiz);
    }

    private double calculateAverageScore(Quiz quiz) {
        if (quiz.getSubmissions() == null || quiz.getSubmissions().isEmpty()) {
            return 0;
        }
        return quiz.getSubmissions().stream()
                .mapToDouble(s -> s.getScore() != null ? s.getScore() : 0)
                .average()
                .orElse(0.0);
    }
}
