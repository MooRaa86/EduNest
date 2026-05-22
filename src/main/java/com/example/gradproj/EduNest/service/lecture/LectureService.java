package com.example.gradproj.EduNest.service.lecture;

import com.example.gradproj.EduNest.dto.lectures.CreateLecturerequest;
import com.example.gradproj.EduNest.dto.lectures.LectureResponse;
import com.example.gradproj.EduNest.dto.lectures.UpdeteLectureRequest;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.entity.lectures.Lecture;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.lectures.LectureRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LectureService {
    private final LectureRepository lectureRepository;
    private final WeekRepository weekRepository;
    private final MentorRepository mentorRepository;

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Unauthenticated user");
        }
        return authentication.getName();
    }

    private Long getCurrentMentorId() {
        return mentorRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new AccessDeniedException("Mentor not found"))
                .getId();
    }

    private Lecture validateMentorOwnsLecture(Long lectureId) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new globalLogicEx("lecture not found"));
        Long mentorId = lecture.getWeek().getMentorship().getMentor().getId();
        if (!mentorId.equals(getCurrentMentorId())) {
            throw new AccessDeniedException("You are not authorized to access this lecture");
        }
        return lecture;
    }

    @PreAuthorize("hasRole('MENTOR')")
    public LectureResponse createLecture(CreateLecturerequest createLecturerequest) {
       Week week=weekRepository.findById(createLecturerequest.getWeekId()).orElseThrow(
               ()->new globalLogicEx("week not found")
       );
       Long mentorId = week.getMentorship().getMentor().getId();
       if (!mentorId.equals(getCurrentMentorId())) {
           throw new AccessDeniedException("You are not authorized to create lectures for this mentorship");
       }
       Lecture lecture=Lecture.builder()
               .title(createLecturerequest.getTitle())
               .lectureUrl(createLecturerequest.getLectureUrl())
               .week(week)
               .build();
       Lecture saved=lectureRepository.save(lecture);

        return mapToLectureResponse(saved);
    }
    @PreAuthorize("hasRole('MENTOR')")
    public void deleteLecture(Long lectureId){
        validateMentorOwnsLecture(lectureId);
        lectureRepository.deleteById(lectureId);
    }
    @PreAuthorize("hasRole('MENTOR')")
    public LectureResponse updateLecture(Long lectureId, UpdeteLectureRequest request){
        Lecture lecture = validateMentorOwnsLecture(lectureId);
        if (request.getTitle() !=null) lecture.setTitle(request.getTitle());
        if (request.getLectureUrl() != null) lecture.setLectureUrl(request.getLectureUrl());
        return mapToLectureResponse(lectureRepository.save(lecture));
    }

    private LectureResponse mapToLectureResponse(Lecture lecture) {
        LectureResponse response=LectureResponse.builder()
                .id(lecture.getId())
                .title(lecture.getTitle())
                .lectureUrl(lecture.getLectureUrl())
                .build();
        return response;
    }
    public LectureResponse getLectureById(Long LectureId){
        Lecture lecture=lectureRepository.findById(LectureId).orElseThrow(
                ()->new globalLogicEx("lecture not found")
        );
        return mapToLectureResponse(lecture);
    }
    public List<LectureResponse> getLecturesByWeekId(Long weekId){
        List<Lecture> lectures=lectureRepository.findByWeek_Id(weekId);
        return lectures.stream().map(this::mapToLectureResponse).toList();
    }

    public PageResponse<LectureResponse> getLecturesByMentorshipId(Long mentorshipId, int page, int size){
        Pageable pageable = PageRequest.of(page, size);
        Page<Lecture> lecturePage = lectureRepository.findByWeek_Mentorship_Id(mentorshipId, pageable);
        List<LectureResponse> lectures = lecturePage.getContent().stream()
                .map(this::mapToLectureResponse)
                .toList();
        return PageResponse.<LectureResponse>builder()
                .content(lectures)
                .page(lecturePage.getNumber())
                .size(lecturePage.getSize())
                .totalElements(lecturePage.getTotalElements())
                .totalPages(lecturePage.getTotalPages())
                .build();
    }
}
