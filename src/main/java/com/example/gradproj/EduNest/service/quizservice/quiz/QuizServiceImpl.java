package com.example.gradproj.EduNest.service.quizservice.quiz;

import com.example.gradproj.EduNest.dto.quizdto.request.QuizDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizDashboardDTO;
import com.example.gradproj.EduNest.dto.quizdto.request.QuizStatisticsDTO;
import com.example.gradproj.EduNest.dto.quizdto.response.QuizResponseDTO;
import com.example.gradproj.EduNest.entity.mentorship.mentorShipE;
import com.example.gradproj.EduNest.entity.quizentity.Quiz;
import com.example.gradproj.EduNest.enums.QuizStatus;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.mentorShipRepository;
import com.example.gradproj.EduNest.repository.quizrepository.QuizRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

@Service
public class QuizServiceImpl implements QuizService {
    @Autowired
    private QuizRepository quizRepository;
    @Autowired
    private mentorShipRepository mentorshipRepository;


    @Override
    public QuizResponseDTO createQuiz(QuizDTO quizdto) {

        mentorShipE mentorship = mentorshipRepository.findById(quizdto.getMentorshipId())
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));


        Quiz quiz = Quiz.builder()
                .title(quizdto.getTitle())
                .duration(quizdto.getDuration())
                .totalPoints(quizdto.getTotalPoints())
                .mentorship(mentorship)
                .status(quizdto.getStatus() != null ? quizdto.getStatus() : QuizStatus.DRAFT)
                .deadline(quizdto.getDeadline())
                .build();

        quiz = quizRepository.save(quiz);

        return QuizResponseDTO.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .durationMinutes(quiz.getDuration())
                .totalPoints(quiz.getTotalPoints())
                .status(quiz.getStatus())
                .deadline(quiz.getDeadline())
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
    public QuizResponseDTO updateQuiz(Long id, QuizDTO quizdto) {
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        mentorShipE mentorship = mentorshipRepository.findById(quizdto.getMentorshipId())
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));

        quiz.setTitle(quizdto.getTitle());
        quiz.setDuration(quizdto.getDuration());
        quiz.setTotalPoints(quizdto.getTotalPoints());
        quiz.setMentorship(mentorship);
        quiz.setStatus(quizdto.getStatus() != null ? quizdto.getStatus() : quiz.getStatus());
        quiz.setDeadline(quizdto.getDeadline());

        quiz = quizRepository.save(quiz);

        return QuizResponseDTO.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .durationMinutes(quiz.getDuration())
                .totalPoints(quiz.getTotalPoints())
                .status(quiz.getStatus())
                .deadline(quiz.getDeadline())
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
                .durationMinutes(quiz.getDuration())
                .totalPoints(quiz.getTotalPoints())
                .status(quiz.getStatus())
                .deadline(quiz.getDeadline())
                .submissions(quiz.getSubmissions() != null ? quiz.getSubmissions().size() : 0)
                .averageScore(calculateAverageScore(quiz))
                .build();
    }

    @Override
    public Page<QuizResponseDTO> getQuizzes(String quizName, QuizStatus status, LocalDate deadline, Pageable pageable) {
        Page<Quiz> quizzes = quizRepository.findQuizzes(quizName, status, deadline, pageable);
        return quizzes.map(quiz -> QuizResponseDTO.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .durationMinutes(quiz.getDuration())
                .totalPoints(quiz.getTotalPoints())
                .status(quiz.getStatus())
                .deadline(quiz.getDeadline())
                .submissions(quiz.getSubmissions() != null ? quiz.getSubmissions().size() : 0)
                .averageScore(calculateAverageScore(quiz))
                .build());

    }

    @Override
    public QuizDashboardDTO getQuizDashboard() {

        List<Quiz> allQuizzes = quizRepository.findAll();

        int totalQuizzes = allQuizzes.size();
//        System.out.println(totalQuizzes);
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


        return QuizStatisticsDTO.builder()
                .status(quiz.getStatus())
                .deadline(quiz.getDeadline())
                .averageScore(calculateAverageScore(quiz))
                .totalStudents(quiz.getMentorship() != null ? quiz.getMentorship().getStudents().size() : 0)
                .totalSubmissions(quiz.getSubmissions() != null ? quiz.getSubmissions().size() : 0)
                .build();

    }

    //we can remove them if unnecessary
    @Override
    public void publishQuiz(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        if (quiz.getStatus() == QuizStatus.PUBLISHED) {
            throw new globalLogicEx("Quiz is already published");
        }

        quiz.setStatus(QuizStatus.PUBLISHED);
        //send notification
        quizRepository.save(quiz);
    }

    @Override
    public void closeQuiz(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new globalLogicEx("Quiz not found"));

        if (quiz.getStatus() != QuizStatus.PUBLISHED) {
            throw new globalLogicEx("Only published quizzes can be closed");
        }

        quiz.setStatus(QuizStatus.CLOSED);
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
