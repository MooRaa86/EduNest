package com.example.gradproj.EduNest.controller.livesession;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.livesession.request.CreateSessionDto;
import com.example.gradproj.EduNest.dto.livesession.request.UpdateSessionDto;
import com.example.gradproj.EduNest.dto.livesession.response.AttendanceResponse;
import com.example.gradproj.EduNest.dto.livesession.response.SessionResponseDto;
import com.example.gradproj.EduNest.service.livesession.LiveSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/liveSession")
@RequiredArgsConstructor
@Tag(
        name = "Live Session",
        description = "APIS"
)
public class LiveSessionController {

    private final LiveSessionService liveSessionService;

    @PostMapping("/create")
    public ResponseEntity<SimpleResponse> createSession(@RequestBody CreateSessionDto createSessionDto) {
        SessionResponseDto sessionResponseDto = liveSessionService.createSession(createSessionDto);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Session created successfully", sessionResponseDto);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/update/{sessionId}")
    public ResponseEntity<SimpleResponse> updateSession(
            @PathVariable Long sessionId,
            @RequestBody UpdateSessionDto updateSessionDto) {

        SessionResponseDto sessionResponseDto =
                liveSessionService.updateSession(sessionId, updateSessionDto);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("Session updated successfully", sessionResponseDto);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SimpleResponse> getSession(@PathVariable Long sessionId) {

        SessionResponseDto sessionResponseDto =
                liveSessionService.getSessionById(sessionId);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("Session fetched successfully", sessionResponseDto);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/delete/{sessionId}")
    public ResponseEntity<SimpleResponse> deleteSession(@PathVariable Long sessionId) {

        liveSessionService.deleteSession(sessionId);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("Message","Session deleted successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/start/{sessionId}")
    public ResponseEntity<SimpleResponse> startSession(@PathVariable Long sessionId) {

        SessionResponseDto sessionResponseDto =
                liveSessionService.startLiveSession(sessionId);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("Live session has been started", sessionResponseDto);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/join/{sessionId}")
    public ResponseEntity<SimpleResponse> joinSession(@PathVariable Long sessionId) {

        SessionResponseDto sessionResponseDto =
                liveSessionService.joinSession(sessionId);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("Joined session successfully", sessionResponseDto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/end/{sessionId}")
    public ResponseEntity<SimpleResponse> endSession(@PathVariable Long sessionId) {

        SessionResponseDto sessionResponseDto =
                liveSessionService.endSession(sessionId);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("Session ended successfully", sessionResponseDto);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Record snapshot", description = "Record attendance snapshot for a session")
    @PostMapping("/snapshot/{sessionId}")
    public ResponseEntity<SimpleResponse> recordSnapshot(@PathVariable Long sessionId,
                                                         @RequestBody List<Long> studentIds) {
        liveSessionService.recordSnapshot(sessionId, studentIds);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Message","Snapshots recorded successfully");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get session attendance", description = "Fetch attendance report for a session")
    @GetMapping("/attendance/{sessionId}")
    public ResponseEntity<SimpleResponse> getAttendance(@PathVariable Long sessionId) {
        List<AttendanceResponse> attendance = liveSessionService.getSessionAttendance(sessionId);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Attendance report for session "+ sessionId, attendance);
        return ResponseEntity.ok(response);
    }
}


