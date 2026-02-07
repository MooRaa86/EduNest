package com.example.gradproj.EduNest.controller.mentorShip;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.ChangeStatusRequest;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipCreateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipUpdateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.mentorShipFDto;
import com.example.gradproj.EduNest.dto.tasks.response.TaskResponse;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.service.mentorShip.mentorShipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mentorship")
@Tag(
        name = "Mentorship controllers",
        description = "all mentorship apis"
)
public class mentorShipControllers {

    private final mentorShipService mentorShipService;

    @GetMapping("/{id}")
    @Operation(summary = "get a mentorship by id")
    public ResponseEntity<SimpleResponse> getMentorShip(
            @PathVariable Long id) {
        mentorShipFDto mentorShip = mentorShipService.getMentorShipById(id);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("mentorShip", mentorShip);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "get pages of mentorship")
    public ResponseEntity<SimpleResponse> getMentorShips(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ){
        PageResponse<mentorShipFDto> mentorShips = mentorShipService.getMentorShips(page, size);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("mentorShips", mentorShips);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "create a mentorship")
    public ResponseEntity<SimpleResponse> createMentorShip(
            @RequestBody @Valid mentorShipCreateDTO dto
    ) {
        mentorShipFDto created = mentorShipService.createMentorShip(dto);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Mentorship created successfully");
        response.addMessage("mentorship", created);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{mid}")
    @Operation(summary = "update a mentorship")
    public ResponseEntity<SimpleResponse> updateMentorShip(
            @PathVariable Long mid,
            @RequestBody @Valid mentorShipUpdateDTO dto
    ) {
        mentorShipFDto updated = mentorShipService.updateMentorShip(mid, dto);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("message","Mentorship updated successfully");
        response.addMessage("mentorship",updated);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{mid}")
    @Operation(summary = "delete a mentorship by id")
    public ResponseEntity<SimpleResponse> deleteMentorShip(
            @PathVariable Long mid) {
        mentorShipService.deleteMentorShip(mid);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Mentorship deleted successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{mid}/get-tasks")
    @Operation(summary = "get mentorship tasks")
    public ResponseEntity<SimpleResponse> getTasks(
            @PathVariable Long mid){
        List<TaskResponse> tasks = mentorShipService.getMentorShipTasks(mid);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("tasks", tasks);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/count-for-mentor")
    @Operation(summary = "get count of all mentorship for the mentor")
    public ResponseEntity<SimpleResponse> getCountForMentor(
            ){
        long count = mentorShipService.countMentorShipsForMentorId();
        SimpleResponse response = new SimpleResponse();
        response.addMessage("count", count);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/count-students-for-mentor")
    @Operation(summary = "get count of students in mentorship for specific mentor")
    public ResponseEntity<SimpleResponse> getCountStudentsForMentor(){
        long count = mentorShipService.countStudentsforMentor();
        SimpleResponse response = new SimpleResponse();
        response.addMessage("count", count);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/update-status/{id}")
    @Operation(summary = "update mentorship status (Draft,Active)")
    public ResponseEntity<SimpleResponse> changeStatus(
            @PathVariable long id,
            @RequestBody ChangeStatusRequest request
    ) {
        mentorShipService.updateMentorShipStatus(id, request.getStatus());

        SimpleResponse response = new SimpleResponse();
        response.addMessage("status", "Status updated successfully");

        return ResponseEntity.ok(response);
    }


    @PostMapping(
            value = "/{mentorshipId}/change-cover-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "upload cover image for mentorship")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<SimpleResponse> uploadCover(
            @PathVariable Long mentorshipId,
            @RequestParam("image") MultipartFile image
    ) {
        String imageURL = mentorShipService.uploadCoverImage(mentorshipId, image);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Image_URL", imageURL);
        return ResponseEntity.ok(response);
    }


}
