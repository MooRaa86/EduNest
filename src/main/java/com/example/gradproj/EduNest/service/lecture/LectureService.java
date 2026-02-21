package com.example.gradproj.EduNest.service.lecture;

import com.example.gradproj.EduNest.dto.lectures.CreateLecturerequest;
import com.example.gradproj.EduNest.dto.lectures.LectureResponse;
import com.example.gradproj.EduNest.dto.lectures.UpdeteLectureRequest;
import com.example.gradproj.EduNest.entity.lectures.Lecture;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.lectures.LectureRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LectureService {
    private final LectureRepository lectureRepository;
    private final WeekRepository weekRepository;
    public LectureResponse createLecture(CreateLecturerequest createLecturerequest) {
       Week week=weekRepository.findById(createLecturerequest.getWeekId()).orElseThrow(
               ()->new globalLogicEx("week not found")
       );
       Lecture lecture=Lecture.builder()
               .title(createLecturerequest.getTitle())
               .lectureUrl(createLecturerequest.getLectureUrl())
               .week(week)
               .build();
       Lecture saved=lectureRepository.save(lecture);

        return mapToLectureResponse(saved);
    }
    public void deleteLecture(Long lectureId){
        if (!(lectureRepository.existsById(lectureId))) {
            throw new globalLogicEx("lecture not found");
        }
        lectureRepository.deleteById(lectureId);
    }
public LectureResponse updateLecture(Long lectureId, UpdeteLectureRequest request){
    Lecture lecture=lectureRepository.findById(lectureId).orElseThrow(
            ()->new globalLogicEx("lecture not found")
    );
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
    public LectureResponse getLectureById (Long LectureId ){
        Lecture lecture=lectureRepository.findById(LectureId).orElseThrow(
                ()->new globalLogicEx("lecture not found")
        );
        return mapToLectureResponse(lecture);
    }
    public List<LectureResponse> getLecturesByWeekId(Long weekId){
        List<Lecture> lectures=lectureRepository.findByWeek_Id(weekId);
        return lectures.stream().map(this::mapToLectureResponse).toList();
    }
}
