package com.example.gradproj.EduNest.service.chat;

import com.example.gradproj.EduNest.dto.chat.ChatRoomResponse;
import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.entity.chat.ChatRoomMember;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.repository.chat.MembersRepo;
import com.example.gradproj.EduNest.repository.chat.projection.ChatRoomProjection;
import com.example.gradproj.EduNest.repository.chat.projection.RoomMemberProjection;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.mentorShip.projections.MentorMentorshipProjection;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.service.mentorShip.ImageStorageService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository roomRepo;
    private final MentorShipRepository mentorshipRepo;
    private final UserRepository userRepo;
    private final MembersRepo membersRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final ImageStorageService imageStorageService;

    @Transactional
    @PreAuthorize("hasRole('MENTOR')")
    public ChatRoomResponse createRoom(
            Long mentorshipId,
            String roomName,
            String creatorEmail
    ) {

        if(!mentorshipRepo.existsById(mentorshipId)) {
            throw new UsernameNotFoundException("Mentorship not found");
        }

        MentorShip mentorship =
                mentorshipRepo.getReferenceById(mentorshipId);

        UserEntity creator = userRepo.getReferenceById(userRepo.findIdByEmail(creatorEmail).orElseThrow(
                () -> new UsernameNotFoundException("User not found")
        ));

        ChatRoom room = ChatRoom.builder()
                .name(roomName)
                .mentorship(mentorship)
                .creator(creator)
                .build();

        ChatRoom savedRoom = roomRepo.save(room);

        ChatRoomMember member = ChatRoomMember.builder()
                .user(creator)
                .chatRoom(savedRoom)
                .joinedAt(LocalDateTime.now())
                .build();

        membersRepo.save(member);

        return mapRoomToResponse(savedRoom, mentorshipId);
    }

    @Transactional
    public void joinRoom(Long roomId,String userEmail) {

        ChatRoom room = roomRepo.findRoomById(roomId).orElseThrow(
                () -> new UsernameNotFoundException("Room not found by id: " + roomId)
        );

        if(membersRepo.existsByChatRoom_IdAndUser_Email(roomId, userEmail)){
            throw new globalLogicEx("User is already joined");
        }

        if (!enrollmentRepo.isUserInRoomMentorship(roomId, userEmail)) {
            throw new globalLogicEx("join the mentorship first :(");
        }

        ChatRoomMember member = ChatRoomMember.builder()
                .chatRoom(room)
                .user(userRepo.getReferenceById(userRepo.findIdByEmail(userEmail).orElseThrow(
                        () -> new UsernameNotFoundException("User not found")
                )))
                .joinedAt(LocalDateTime.now())
                .build();

        membersRepo.save(member);

    }

    @Transactional
    @PreAuthorize("hasRole('MENTOR')")
    public String updateRoomImage(Long roomId, String userEmail, MultipartFile image) {
        ChatRoom room = roomRepo.findRoomById(roomId).orElseThrow(
                () -> new UsernameNotFoundException("Room not found")
        );

        if (!room.getCreator().getEmail().equals(userEmail)) {
            throw new globalLogicEx("Only room creator can update image");
        }

        if (image != null && !image.isEmpty()) {
            imageStorageService.deleteImage("chatroom", room.getImageUrl());
            String newImageUrl = imageStorageService.saveImage("chatroom", room.getId(), image);
            room.setImageUrl(newImageUrl);
            roomRepo.save(room);
            return newImageUrl;
        }
        throw new globalLogicEx("Image is empty");
    }

    @Transactional
    public List<RoomMemberProjection> getRoomMembers(Long roomId) {
        return membersRepo.findRoomMembers(roomId);
    }


    public List<ChatRoomProjection> getRoomsforMentorship(Long mentorshipId,String studentEmail) {
        Long studentId = userRepo.findIdByEmail(studentEmail).orElseThrow(
                () -> new UsernameNotFoundException("Student not found")
        );

        if(!mentorshipRepo.existsById(mentorshipId)){
            throw new UsernameNotFoundException("Mentorship not found");
        }

        if(!(enrollmentRepo.existsByMentorShip_IdAndStudent_Id(mentorshipId,studentId))){
            throw new globalLogicEx("you can't view this groups enroll first");
        }
        return roomRepo.findRoomsByMentorship(mentorshipId);
    }

    public List<ChatRoomProjection> getUserRooms(String userEmail) {
        return roomRepo.findRoomsByUserEmail(userEmail);
    }

    public List<MentorMentorshipProjection> getMentorMentorships(String mentorEmail) {
        return mentorshipRepo.findMentorMentorshipsForChatRoom(mentorEmail);
    }

    @Transactional
    @PreAuthorize("hasRole('MENTOR')")
    public void deleteRoom(Long roomId, String userEmail) {
        ChatRoom room = roomRepo.findRoomById(roomId).orElseThrow(
                () -> new UsernameNotFoundException("Room not found")
        );

        if (!room.getCreator().getEmail().equals(userEmail)) {
            throw new globalLogicEx("Only room creator can delete the room");
        }

        roomRepo.delete(room);
    }

    private ChatRoomResponse mapRoomToResponse(ChatRoom room,Long mentorshipId) {
        return ChatRoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .mentorshipId(mentorshipId)
                .build();
    }
}
