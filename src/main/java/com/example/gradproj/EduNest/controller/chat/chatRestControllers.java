package com.example.gradproj.EduNest.controller.chat;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.chat.ChatMessageResponse;
import com.example.gradproj.EduNest.dto.chat.ChatRoomResponse;
import com.example.gradproj.EduNest.dto.chat.roomCreateDto;
import com.example.gradproj.EduNest.repository.chat.projection.ChatRoomProjection;
import com.example.gradproj.EduNest.service.chat.ChatMessageService;
import com.example.gradproj.EduNest.service.chat.ChatRoomService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/chat-room")
@RequiredArgsConstructor
public class chatRestControllers {
    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;

    @PostMapping("/create/{mentorshipId}")
    @Operation(summary = "create room for mentorship by it's id")
    public ResponseEntity<SimpleResponse> createRoom(
            @PathVariable Long mentorshipId,
            @RequestBody roomCreateDto dto,
            Principal principal
    ) {

        ChatRoomResponse response =
                chatRoomService.createRoom(
                        mentorshipId,
                        dto.getName(),
                        principal.getName() // email of the creator from context
                );
        SimpleResponse resp = new SimpleResponse();

        resp.addMessage("Room",response);
        resp.addMessage("status","room created successfully");

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{mid}")
    @Operation(summary = "get rooms for the mentorship by it's id")
    public ResponseEntity<SimpleResponse> getRoomsByMid(
            @PathVariable Long mid
    ){
        List<ChatRoomProjection> rooms = chatRoomService.getRoomsforMentorship(mid);
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("Rooms",rooms);
        resp.addMessage("status","rooms founded successfully");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<SimpleResponse> getMessages(
            @PathVariable Long roomId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") int size
    ) {

        List<ChatMessageResponse> messages =  chatMessageService.getRoomMessages(roomId, beforeId, size);

        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("Messages",messages);
        resp.addMessage("status","messages founded successfully");
        return ResponseEntity.ok(resp);
    }

}
