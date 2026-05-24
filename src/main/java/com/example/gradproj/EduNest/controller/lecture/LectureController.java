package com.example.gradproj.EduNest.controller.lecture;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.lectures.CreateLecturerequest;
import com.example.gradproj.EduNest.dto.lectures.UpdeteLectureRequest;
import com.example.gradproj.EduNest.service.lecture.LectureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/lectures")
@RequiredArgsConstructor
@Tag(
        name = "Lectures",
        description = "APIS of lectures functionality create , delete , update ,get lecture by it's id"
)
@PreAuthorize("hasRole('MENTOR')")
public class LectureController {
    private final LectureService lectureService;
    @PostMapping
    @Operation(summary = "create Lecture for Week")

    public ResponseEntity<SimpleResponse> createLecture(@RequestBody CreateLecturerequest createLecturerequest){
       SimpleResponse simpleResponse=new SimpleResponse();
       simpleResponse.addMessage("message","lecture created successfully");
       simpleResponse.addMessage("lecture",lectureService.createLecture(createLecturerequest));
       return ResponseEntity.status(HttpStatus.CREATED).body(simpleResponse);
    }
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete lecture from week by it's id")

    public ResponseEntity<SimpleResponse> deleteLecture(@PathVariable Long id){
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message","lecture deleted successfully");
        lectureService.deleteLecture(id);
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }
    @PatchMapping("/{id}")
    @Operation(summary = "Update lecture from week by it's id")

    public ResponseEntity<SimpleResponse> updateLecture(@PathVariable Long id,@RequestBody UpdeteLectureRequest updeteLectureRequest){
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message","lecture updated successfully");
        simpleResponse.addMessage("lecture",lectureService.updateLecture(id,updeteLectureRequest));
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }
    @GetMapping("/{id}")
    @Operation(summary = "get lecture by it's id")
    public ResponseEntity<SimpleResponse> getLectureById(@PathVariable Long id){
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message","lecture fetched successfully");
        simpleResponse.addMessage("lecture",lectureService.getLectureById(id));
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }
    @GetMapping("/week/{weekId}")
    @Operation(summary = "Get all lectures by week id")
    public ResponseEntity<SimpleResponse> getLecturesByWeekId(@PathVariable Long weekId) {

        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "lectures fetched successfully");
        simpleResponse.addMessage("lectures", lectureService.getLecturesByWeekId(weekId));

        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

    @GetMapping("/mentorship/{mentorshipId}")
    @Operation(summary = "Get all lectures by mentorship id")
    public ResponseEntity<SimpleResponse> getLecturesByMentorshipId(
            @PathVariable Long mentorshipId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.addMessage("message", "lectures fetched successfully");
        simpleResponse.addMessage("lectures", lectureService.getLecturesByMentorshipId(mentorshipId, page, size));
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }


}
