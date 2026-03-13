package com.example.gradproj.EduNest.controller.chat;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.chat.ChatMessageResponse;
import com.example.gradproj.EduNest.dto.chat.ChatRoomResponse;
import com.example.gradproj.EduNest.dto.chat.roomCreateDto;
import com.example.gradproj.EduNest.repository.chat.projection.ChatRoomProjection;
import com.example.gradproj.EduNest.repository.chat.projection.RoomMemberProjection;
import com.example.gradproj.EduNest.service.chat.ChatMessageService;
import com.example.gradproj.EduNest.service.chat.ChatRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/chat-room")
@RequiredArgsConstructor
@Tag(
        name = "Chat Rooms",
        description = "create, join room, get messages..."
)
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
    @Operation(summary = "get latest messages of the room")
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

    @GetMapping("/{roomId}/members")
    @Operation(summary = "get room members")
    public ResponseEntity<SimpleResponse> getRoomMembers(
            @PathVariable Long roomId
    ) {
        List<RoomMemberProjection> members =  chatRoomService.getRoomMembers(roomId);
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("members",members);
        resp.addMessage("status","members founded successfully");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{roomId}/join")
    @Operation(summary = "join room")
    public ResponseEntity<SimpleResponse> joinRoom(
            @PathVariable Long roomId,
            Authentication authentication
    ) {

        chatRoomService.joinRoom(
                roomId,
                authentication.getName() // email
        );

        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("status", authentication.getName() + " joined success");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/my-rooms")
    @Operation(summary = "get all rooms that user is member of")
    public ResponseEntity<SimpleResponse> getMyRooms(
            Authentication authentication
    ) {
        List<ChatRoomProjection> rooms = chatRoomService.getUserRooms(authentication.getName());
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("Rooms", rooms);
        resp.addMessage("status", "rooms founded successfully");
        return ResponseEntity.ok(resp);
    }

    @PutMapping(value = "/{roomId}/image",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "update chat room image")
    public ResponseEntity<SimpleResponse> updateRoomImage(
            @PathVariable Long roomId,
            @RequestParam("image") MultipartFile image,
            Authentication authentication
    ) {
        String imageUrl = chatRoomService.updateRoomImage(roomId, authentication.getName(), image);
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("imageUrl", imageUrl);
        resp.addMessage("status", "room image updated successfully");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/mentor/mentorships")
    @Operation(summary = "get mentor mentorships for creating chat rooms")
    public ResponseEntity<SimpleResponse> getMentorMentorships(Authentication authentication) {
        var mentorships = chatRoomService.getMentorMentorships(authentication.getName());
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("mentorships", mentorships);
        resp.addMessage("status", "mentorships retrieved successfully");
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/{roomId}")
    @Operation(summary = "delete chat room (creator only)")
    public ResponseEntity<SimpleResponse> deleteRoom(
            @PathVariable Long roomId,
            Authentication authentication
    ) {
        chatRoomService.deleteRoom(roomId, authentication.getName());
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("status", "room deleted successfully");
        return ResponseEntity.ok(resp);
    }

}
