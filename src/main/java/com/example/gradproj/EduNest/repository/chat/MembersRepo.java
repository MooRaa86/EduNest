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
        CASE WHEN m.user.deleted = true AND :revealDeleted = false THEN NULL ELSE m.user.publicId END as userId,
        CASE WHEN m.user.deleted = true AND :revealDeleted = false THEN NULL ELSE m.user.email END as email,
        CASE WHEN m.user.deleted = true AND :revealDeleted = false THEN 'Deleted user' ELSE m.user.firstName END as firstName,
        CASE WHEN m.user.deleted = true AND :revealDeleted = false THEN NULL ELSE m.user.lastName END as lastName,
        CASE WHEN m.user.deleted = true AND :revealDeleted = false THEN NULL ELSE m.user.role.name END as role,
        CASE WHEN m.user.deleted = true AND :revealDeleted = false THEN NULL ELSE m.user.profileImageUrl END as userImageUrl
    FROM ChatRoomMember m
    WHERE m.chatRoom.publicId = :roomPublicId
""")
    List<RoomMemberProjection> findRoomMembersByPublicId(UUID roomPublicId, boolean revealDeleted);

    boolean existsByChatRoom_IdAndUser_Email(
            Long roomId,
            String email
    );

    @Query("""
    SELECT
        CASE WHEN m.user.deleted = true AND :revealDeleted = false THEN NULL ELSE m.user.publicId END as userId,
        CASE WHEN m.user.deleted = true AND :revealDeleted = false THEN NULL ELSE m.user.email END as email,
        CASE WHEN m.user.deleted = true AND :revealDeleted = false THEN 'Deleted user' ELSE m.user.firstName END as firstName,
        CASE WHEN m.user.deleted = true AND :revealDeleted = false THEN NULL ELSE m.user.lastName END as lastName,
        CASE WHEN m.user.deleted = true AND :revealDeleted = false THEN NULL ELSE m.user.role.name END as role,
        CASE WHEN m.user.deleted = true AND :revealDeleted = false THEN NULL ELSE m.user.profileImageUrl END as userImageUrl
    FROM ChatRoomMember m
    WHERE m.chatRoom.id = :roomId
""")
    List<RoomMemberProjection> findRoomMembers(Long roomId, boolean revealDeleted);
}
