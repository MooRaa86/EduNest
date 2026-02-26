package com.example.gradproj.EduNest.controller.profile;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.profile.EnrolledMentorshipProgressDto;
import com.example.gradproj.EduNest.dto.profile.FullProfileStudentInformationForMentorResponse;
import com.example.gradproj.EduNest.dto.profile.StudentProjectProfileDTO;
import com.example.gradproj.EduNest.enums.tasks.SubmissionStatus;
import com.example.gradproj.EduNest.service.profile.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/profile/students")
@RequiredArgsConstructor
@Tag(
        name = "Student profile for mentor",
        description = "student profile statistics"
)
public class MentorViewStudentProfileController {

    private final ProfileService profileService;

    @GetMapping("/{studentId}")
    @Operation(summary = "get cards and student info")
    public ResponseEntity<SimpleResponse> getStudentProfile(
            @PathVariable Long studentId
    ){
     SimpleResponse response=new SimpleResponse();
     response.addMessage("status","Student profile retrieved successfully");
     response.addMessage("Student profile",profileService.profileStudentInformationForMentorResponse(studentId));
     return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/students/{studentId}/mentorships-progress")
    @Operation(summary = "Get enrolled mentorships progress for a student")
    public ResponseEntity<SimpleResponse> getStudentMentorshipProgress(
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size
    ) {

        PageResponse<EnrolledMentorshipProgressDto> progress =
                profileService.getEnrolledMentorshipProgress(studentId, PageRequest.of(page, size));

        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("mentorshipProgress", progress);

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/students/{studentId}/projects")
    @Operation(summary = "Get student projects for profile")
    public ResponseEntity<SimpleResponse> getStudentProjects(
            @PathVariable Long studentId,
            @RequestParam(required = false) SubmissionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size
    ) {

        PageResponse<StudentProjectProfileDTO> projects =
                profileService.getStudentProjects(
                        studentId,
                        status,
                        page,
                        size
                );

        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("studentProjects", projects);

        return ResponseEntity.ok(resp);
    }
    @GetMapping("/{studentId}/full-profile")
    @Operation(summary = "Get full student profile (info + mentorship progress + projects)")
    public ResponseEntity<SimpleResponse> getFullStudentProfile(
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "0") int mentorshipsPage,
            @RequestParam(defaultValue = "6") int mentorshipsSize,
            @RequestParam(required = false) SubmissionStatus projectsStatus,
            @RequestParam(defaultValue = "0") int projectsPage,
            @RequestParam(defaultValue = "6") int projectsSize
    ) {

        FullProfileStudentInformationForMentorResponse data =
                profileService.getFullStudentProfileForMentor(
                        studentId,
                        mentorshipsPage,
                        mentorshipsSize,
                        projectsPage,
                        projectsSize,
                        projectsStatus
                );

        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("status", "Student full profile retrieved successfully");
        resp.addMessage("studentFullProfile", data);

        return ResponseEntity.status(HttpStatus.OK).body(resp);
    }


}
