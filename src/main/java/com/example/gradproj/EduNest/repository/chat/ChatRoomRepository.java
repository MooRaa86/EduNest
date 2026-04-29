package com.example.gradproj.EduNest.repository.chat;

import com.example.gradproj.EduNest.dto.chat.MentorshipRoomDetailsResponse;
import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.repository.chat.projection.ChatRoomProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("""
    select
        cr.id as roomId,
        cr.name as roomName,
        cr.imageUrl as roomImageUrl,
        m.id as mentorshipId,
        m.title as mentorshipName,
        c.email as creatorEmail,
        concat(c.firstName, ' ', c.lastName) as creatorName,
        lastMsg.content as lastMessageContent,
        lastMsg.createdAt as lastMessageTime,
        lastMsg.senderEmail as lastMessageSenderEmail,
        lastMsg.senderName as lastMessageSenderName
    from ChatRoom cr
    join cr.mentorship m
    join cr.creator c
    left join ChatMessage lastMsg on lastMsg.chatRoom.id = cr.id
        and lastMsg.createdAt = (
            select max(msg.createdAt)
            from ChatMessage msg
            where msg.chatRoom.id = cr.id
        )
    where m.id = :mentorshipId
""")
    List<ChatRoomProjection> findRoomsByMentorship(Long mentorshipId);

    @Query("""
   SELECT r
   FROM ChatRoom r
   WHERE r.id = :id
""")
    Optional<ChatRoom> findRoomById(Long id);

    @Query("""
    select
        cr.id as roomId,
        cr.name as roomName,
        cr.imageUrl as roomImageUrl,
        m.id as mentorshipId,
        m.title as mentorshipName,
        c.email as creatorEmail,
        concat(c.firstName, ' ', c.lastName) as creatorName,
        lastMsg.content as lastMessageContent,
        lastMsg.createdAt as lastMessageTime,
        lastMsg.senderEmail as lastMessageSenderEmail,
        lastMsg.senderName as lastMessageSenderName
    from ChatRoom cr
    join cr.mentorship m
    join cr.creator c
    join cr.members crm
    left join ChatMessage lastMsg on lastMsg.chatRoom.id = cr.id
        and lastMsg.createdAt = (
            select max(msg.createdAt)
            from ChatMessage msg
            where msg.chatRoom.id = cr.id
        )
    where crm.user.email = :userEmail
""")
    List<ChatRoomProjection> findRoomsByUserEmail(String userEmail);

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.chat.MentorshipRoomDetailsResponse(
        cr.id,
        cr.name,
        cr.imageUrl,
        CASE WHEN EXISTS (
            SELECT 1 FROM ChatRoomMember crm
            WHERE crm.chatRoom.id = cr.id
            AND crm.user.email = :studentEmail
        ) THEN true ELSE false END
    )
    FROM ChatRoom cr
    JOIN cr.mentorship m
    WHERE m.id = :mentorshipId
""")
    List<MentorshipRoomDetailsResponse> findRoomsWithJoinStatusByMentorship(
            @Param("mentorshipId") Long mentorshipId,
            @Param("studentEmail") String studentEmail
    );

}
