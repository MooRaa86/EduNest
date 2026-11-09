package com.example.gradproj.EduNest.repository.chat;

import com.example.gradproj.EduNest.entity.chat.ChatRoomMember;
import com.example.gradproj.EduNest.repository.chat.projection.RoomMemberProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface MembersRepo extends JpaRepository<ChatRoomMember, Long> {

    boolean existsByChatRoom_PublicIdAndUser_Email(
            UUID roomPublicId,
            String email
    );

    @Query("""
    SELECT
        m.user.publicId as userId,
        m.user.email as email,
        m.user.firstName as firstName,
        m.user.lastName as lastName,
        m.user.role.name as role,
        m.user.profileImageUrl as userImageUrl
    FROM ChatRoomMember m
    WHERE m.chatRoom.publicId = :roomPublicId
""")
    List<RoomMemberProjection> findRoomMembersByPublicId(UUID roomPublicId);

    boolean existsByChatRoom_IdAndUser_Email(
            Long roomId,
            String email
    );

    @Query("""
    SELECT
        m.user.publicId as userId,
        m.user.email as email,
        m.user.firstName as firstName,
        m.user.lastName as lastName,
        m.user.role.name as role,
        m.user.profileImageUrl as userImageUrl
    FROM ChatRoomMember m
    WHERE m.chatRoom.id = :roomId
""")
    List<RoomMemberProjection> findRoomMembers(Long roomId);
}
