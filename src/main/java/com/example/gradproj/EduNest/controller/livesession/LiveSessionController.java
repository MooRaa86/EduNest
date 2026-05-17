package com.example.gradproj.EduNest.controller.livesession;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.livesession.request.CreateSessionDto;
import com.example.gradproj.EduNest.dto.livesession.request.UpdateSessionDto;
import com.example.gradproj.EduNest.dto.livesession.response.AttendanceResponse;
import com.example.gradproj.EduNest.dto.livesession.response.DashboardSessionResponse;
import com.example.gradproj.EduNest.dto.livesession.response.SessionResponseDto;
import com.example.gradproj.EduNest.dto.livesession.response.StudentUpcomingSessionResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.service.livesession.LiveSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/liveSession")
@RequiredArgsConstructor
@Tag(name = "Live Session", description = "APIs for managing live sessions (create, update, start, join, end, attendance)")
public class LiveSessionController {

    private final LiveSessionService liveSessionService;

    @Operation(summary = "Create a new live session", description = "Create a live session and get its details")
    @PostMapping("/create")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> createSession(@RequestBody CreateSessionDto createSessionDto) {
        SessionResponseDto sessionResponseDto = liveSessionService.createSession(createSessionDto);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Session created successfully", sessionResponseDto);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update live session", description = "Update an existing live session by session ID")
    @PatchMapping("/update/{sessionId}")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> updateSession(
            @PathVariable Long sessionId,
            @RequestBody UpdateSessionDto updateSessionDto) {

        SessionResponseDto sessionResponseDto =
                liveSessionService.updateSession(sessionId, updateSessionDto);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("Session updated successfully", sessionResponseDto);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get live session details",
            description = "Retrieve details of a live session using session ID"
    )
    @GetMapping("/{sessionId}")
    public ResponseEntity<SimpleResponse> getSession(@PathVariable Long sessionId) {

        SessionResponseDto sessionResponseDto =
                liveSessionService.getSessionById(sessionId);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("Session fetched successfully", sessionResponseDto);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get All live sessions for specific mentorship", description = "Retrieve details of all live sessions using mentorship ID")
    @GetMapping("/mentorship")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> getAllSession(
            @RequestParam Long mentorshipId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {

        PageResponse<DashboardSessionResponse> sessions =
                liveSessionService.getAllSessions(mentorshipId,page,size);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("Sessions fetched successfully", sessions);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete live session", description = "Delete a live session by session ID")
    @DeleteMapping("/delete/{sessionId}")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> deleteSession(@PathVariable Long sessionId) {

        liveSessionService.deleteSession(sessionId);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("Message", "Session deleted successfully");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Start live session", description = "Start a live session by session ID")
    @PostMapping("/start/{sessionId}")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> startSession(@PathVariable Long sessionId) {

        SessionResponseDto sessionResponseDto =
                liveSessionService.startLiveSession(sessionId);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("Live session has been started", sessionResponseDto);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Join live session", description = "Join a live session using session ID")
    @GetMapping("/join/{sessionId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SimpleResponse> joinSession(@PathVariable Long sessionId) {

        SessionResponseDto sessionResponseDto =
                liveSessionService.joinSession(sessionId);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("Joined session successfully", sessionResponseDto);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "End live session", description = "End an ongoing live session by session ID")
    @PostMapping("/end/{sessionId}")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> endSession(@PathVariable Long sessionId) {

        SessionResponseDto sessionResponseDto =
                liveSessionService.endSession(sessionId);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("Session ended successfully", sessionResponseDto);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Record attendance snapshot", description = "Record attendance snapshot for a session by providing student IDs")
    @PostMapping("/snapshot/{sessionId}")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> recordSnapshot(@PathVariable Long sessionId,
                                                         @RequestBody List<Long> studentIds) {
        liveSessionService.recordSnapshot(sessionId, studentIds);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Message", "Snapshots recorded successfully");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get session attendance", description = "Fetch attendance report for a session using session ID")
    @GetMapping("/attendance/{sessionId}")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> getAttendance(@PathVariable Long sessionId) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("attendance", liveSessionService.getAttendanceResult(sessionId));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get my attendance result", description = "Fetch attendance result for the authenticated student in a session")
    @GetMapping("/myAttendance/{sessionId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SimpleResponse> getStudentAttendance(
            @PathVariable Long sessionId) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("attendance", liveSessionService.getStudentAttendanceResult(sessionId));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get upcoming sessions for student", description = "Fetch all upcoming sessions for the authenticated student across all enrolled mentorships")
    @GetMapping("/student/upcoming")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SimpleResponse> getUpcomingSessionsForStudent(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageResponse<StudentUpcomingSessionResponse> upcomingSessions =
                liveSessionService.getUpcomingSessionsForStudent(page, size);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("Upcoming sessions fetched successfully", upcomingSessions);
        return ResponseEntity.ok(response);
    }
}


