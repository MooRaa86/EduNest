package com.example.gradproj.EduNest.service.chat;

import com.example.gradproj.EduNest.dto.chat.ChatRoomResponse;
import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.repository.chat.projection.ChatRoomProjection;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository roomRepo;
    private final MentorShipRepository mentorshipRepo;
    private final UserRepository userRepo;

    @Transactional
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

        return mapRoomToResponse(savedRoom, mentorshipId);
    }


    public List<ChatRoomProjection> getRoomsforMentorship(Long mentorshipId) {
        return roomRepo.findRoomsByMentorship(mentorshipId);
    }

    private ChatRoomResponse mapRoomToResponse(ChatRoom room,Long mentorshipId) {
        return ChatRoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .mentorshipId(mentorshipId)
                .build();
    }
}
