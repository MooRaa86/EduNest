package com.example.gradproj.EduNest.service.week;

import com.example.gradproj.EduNest.dto.weeks.*;
import com.example.gradproj.EduNest.entity.livesession.Session;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.projects.Project;
import com.example.gradproj.EduNest.entity.quiz.Quiz;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.lectures.LectureRepository;
import com.example.gradproj.EduNest.repository.livesession.LiveSessionRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.projects.ProjectRepository;
import com.example.gradproj.EduNest.repository.quiz.QuizRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WeekService {
    private final MentorShipRepository mentorShipRepository;
    private final WeekRepository weekRepository;
    private final TaskRepository taskRepository;
    private final QuizRepository quizRepository;
    private final ProjectRepository projectRepository;
    private final LiveSessionRepository sessionRepository;
    private final LectureRepository lectureRepository;


    public WeekResponse createWeek(CreateWeekrequest createWeekrequest) {
        MentorShip mentorShip = mentorShipRepository.findById(createWeekrequest.getMentorshipId()).orElseThrow(
                () -> new globalLogicEx("mentorShip not found")
        );
        Week week = Week.builder()
                .title(createWeekrequest.getTitle())
                .mentorship(mentorShip)
                .build();
        Week saved = weekRepository.save(week);
        return mapToWeekResponse(saved);

    }

    public void deleteWeek(Long weekId) {
        if (!weekRepository.existsById(weekId)) {
            throw new globalLogicEx("Week not found");
        }
        weekRepository.deleteById(weekId);
    }

    public WeekResponse udateWeekTitle(Long id, UpdateWeekRequest request) {
        Week week = weekRepository.findById(id).orElseThrow(() -> new globalLogicEx("Week not found"));
        week.setTitle(request.getTitle());
        return mapToWeekResponse(weekRepository.save(week));

    }

    @Transactional(readOnly = true)
    public List<WeekResponse> getWeeksByMentorship(Long mentorshipId) {
        return weekRepository.findByMentorship_IdOrderByIdAsc(mentorshipId)
                .stream().map(this::mapToWeekResponse).toList();
    }


    @Transactional(readOnly = true)
    public WeekContentsResponse getWeekContents(Long weekId) {

        Week week = weekRepository.findById(weekId)
                .orElseThrow(() -> new globalLogicEx("Week not found"));

        var tasks = taskRepository.findByWeek_Id(weekId);
        var quizzes = quizRepository.findByWeek_Id(weekId);
        var projects = projectRepository.findByWeek_Id(weekId);
        var sessions = sessionRepository.findByWeek_Id(weekId);
        var lectures = lectureRepository.findByWeek_Id(weekId);

        List<WeekContentItemDTO> items = new ArrayList<>();

        // Session
        for (Session s : sessions) {
            items.add(WeekContentItemDTO.builder()
                    .type("SESSION")
                    .id(s.getId())
                    .title(s.getTitle())
                    .status(s.getStatus() == null ? null : s.getStatus().name())
                    .createdAt(s.getCreatedAt())
                    .build());
        }

        // Quiz
        for (Quiz q : quizzes) {
            items.add(WeekContentItemDTO.builder()
                    .type("QUIZ")
                    .id(q.getId())
                    .title(q.getTitle())
                    .status(q.getStatus() == null ? null : q.getStatus().name())
                    .createdAt(q.getCreatedAt())
                    .build());
        }

        // Task
        for (Task t : tasks) {
            items.add(WeekContentItemDTO.builder()
                    .type("TASK")
                    .id(t.getId())
                    .title(t.getTitle())
                    .status(t.getStatus() == null ? null : t.getStatus().name())
                    .createdAt(t.getCreatedAt())
                    .build());
        }

        // Project
        for (Project p : projects) {
            items.add(WeekContentItemDTO.builder()
                    .type("PROJECT")
                    .id(p.getId())
                    .title(p.getTitle())
                    .status(p.getStatus() == null ? null : p.getStatus().name())
                    .createdAt(p.getCreatedAt())
                    .build());
        }


        // ✅ ترتيب زي UI: Session ثم Quiz ثم Task ثم Project (مؤقت لحد ما تعمل orderIndex)
//        items.sort(Comparator
//                .comparingInt((WeekContentItemDTO i) -> typePriority(i.getType()))
//                .thenComparing(WeekContentItemDTO::getId, Comparator.nullsLast(Long::compareTo))
//        );

        return WeekContentsResponse.builder()
                .weekId(week.getId())
                .weekTitle(week.getTitle())
                .items(items)
                .build();
    }


    private WeekResponse mapToWeekResponse(Week week) {
        WeekResponse res = new WeekResponse();
        res.setTitle(week.getTitle());
        res.setId(week.getId());
        return res;
    }

    private int typePriority(String type) {
        return switch (type) {
            case "SESSION" -> 1;
            case "QUIZ" -> 2;
            case "TASK" -> 3;
            case "PROJECT" -> 4;
            default -> 99;
        };
    }
}
