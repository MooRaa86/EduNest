package com.example.gradproj.EduNest.controller.mentorShip;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipCreateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipUpdateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.mentorShipFDto;
import com.example.gradproj.EduNest.service.mentorShip.mentorShipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/mentorship")
public class mentorShipControllers {

    private final mentorShipService mentorShipService;

    @GetMapping("/{id}")
    public ResponseEntity<SimpleResponse> getMentorShip(
            @PathVariable Long id) {
        mentorShipFDto mentorShip = mentorShipService.getMentorShipById(id);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("mentorShip", mentorShip);
        return ResponseEntity.ok(response);
    }

    @GetMapping
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
    public ResponseEntity<SimpleResponse> deleteMentorShip(
            @PathVariable Long mid) {
        mentorShipService.deleteMentorShip(mid);
        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "Mentorship deleted successfully");
        return ResponseEntity.ok(response);
    }


}
