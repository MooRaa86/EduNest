package com.example.gradproj.EduNest.repository.chat;

import com.example.gradproj.EduNest.entity.chat.ChatRoomMember;
import com.example.gradproj.EduNest.repository.chat.projection.RoomMemberProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface MembersRepo extends JpaRepository<ChatRoomMember, Long> {
    boolean existsByChatRoom_IdAndUser_Email(
            Long roomId,
            String email
    );

    @Query("""
    SELECT
        m.user.id as userId,
        m.user.email as email,
        m.user.firstName as firstName,
        m.user.lastName as lastName,
        m.user.role.name as role
    FROM ChatRoomMember m
    WHERE m.chatRoom.id = :roomId
""")
    List<RoomMemberProjection> findRoomMembers(Long roomId);
}
