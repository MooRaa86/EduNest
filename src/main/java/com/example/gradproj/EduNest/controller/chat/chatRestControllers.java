package com.example.gradproj.EduNest.controller.chat;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.chat.ChatMessageResponse;
import com.example.gradproj.EduNest.dto.chat.ChatRoomResponse;
import com.example.gradproj.EduNest.dto.chat.MentorshipRoomDetailsResponse;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;

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
    private final SimpMessagingTemplate messagingTemplate;

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
            @PathVariable Long mid,
            Authentication authentication
    ){
        String email = authentication.getName();
        List<ChatRoomProjection> rooms = chatRoomService.getRoomsforMentorship(mid,email);
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
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {

        List<ChatMessageResponse> messages =  chatMessageService.getRoomMessages(roomId, beforeId, size,authentication.getName());

        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("Messages",messages);
        resp.addMessage("status","messages founded successfully");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{roomId}/members")
    @Operation(summary = "get room members")
    public ResponseEntity<SimpleResponse> getRoomMembers(
            @PathVariable Long roomId,
            Authentication authentication
    ) {
        List<RoomMemberProjection> members =  chatRoomService.getRoomMembers(roomId,authentication.getName());
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("members",members);
        resp.addMessage("status","members founded successfully");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/mentorship/{mentorshipId}/rooms-with-status")
    @Operation(summary = "get rooms for mentorship with student join status")
    public ResponseEntity<SimpleResponse> getMentorshipRoomsWithStatus(
            @PathVariable Long mentorshipId,
            Authentication authentication
    ) {
        List<MentorshipRoomDetailsResponse> rooms = chatRoomService.getRoomsWithJoinStatus(
                mentorshipId,
                authentication.getName()
        );
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("rooms", rooms);
        resp.addMessage("status", "rooms retrieved successfully");
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
        resp.addMessage("status", "joined success");
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

    @PatchMapping("/{roomId}/messages/{messageId}")
    @Operation(summary = "edit chat message (sender only)")
    public ResponseEntity<SimpleResponse> editMessage(
            @PathVariable Long roomId,
            @PathVariable Long messageId,
            @RequestParam String content,
            Authentication authentication
    ) {
        ChatMessageResponse updated = chatMessageService.editMessage(
                messageId,
                authentication.getName(),
                content
        );
        
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                updated
        );
        
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("message", updated);
        resp.addMessage("status", "message updated successfully");
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/{roomId}/messages/{messageId}")
    @Operation(summary = "delete chat message (sender only)")
    public ResponseEntity<SimpleResponse> deleteMessage(
            @PathVariable Long roomId,
            @PathVariable Long messageId,
            Authentication authentication
    ) {
        chatMessageService.deleteMessage(messageId, authentication.getName());
        
        Map<String, Object> event = Map.of(
                "type", "DELETE",
                "messageId", messageId
        );
        
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                event
        );
        
        SimpleResponse resp = new SimpleResponse();
        resp.addMessage("status", "message deleted successfully");
        return ResponseEntity.ok(resp);
    }

}
