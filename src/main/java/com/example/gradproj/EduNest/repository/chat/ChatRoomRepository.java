package com.example.gradproj.EduNest.repository.chat;

import com.example.gradproj.EduNest.dto.chat.MentorshipRoomDetailsResponse;
import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.repository.chat.projection.ChatRoomProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("SELECT e FROM ChatRoom e WHERE e.publicId = :publicId")
    Optional<ChatRoom> findByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<ChatRoom> findByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<ChatRoom> entity = findByPublicIdDirect(publicId);
        if (entity.isPresent()) return entity;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits());
        }
        return Optional.empty();
    }
    boolean existsByPublicId(UUID publicId);
    void deleteByPublicId(UUID publicId);

    @Query("SELECT cr.id FROM ChatRoom cr WHERE cr.publicId = :publicId")
    Optional<Long> findIdByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<Long> findIdByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<Long> id = findIdByPublicIdDirect(publicId);
        if (id.isPresent()) return id;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits()).map(ChatRoom::getId);
        }
        return Optional.empty();
    }

    @Query("""
    SELECT COUNT(cr) > 0
    FROM ChatRoom cr
    WHERE cr.publicId = :roomPublicId
    AND cr.mentorship.mentor.email = :email
""")
    Boolean isRoomOwnedByMentor(@Param("roomPublicId") UUID roomPublicId,
                                @Param("email") String email);


    @Query("""
    select
        cr.publicId as roomId,
        cr.name as roomName,
        cr.imageUrl as roomImageUrl,
        m.publicId as mentorshipId,
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
        cr.publicId as roomId,
        cr.name as roomName,
        cr.imageUrl as roomImageUrl,
        m.publicId as mentorshipId,
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
    SELECT COUNT(cr) > 0
    FROM ChatRoom cr
    WHERE cr.id = :roomId
    AND cr.mentorship.mentor.email = :email
""")
    Boolean isRoomOwnedByMentor(@Param("roomId") Long roomId,
                                @Param("email") String email);

    @Query("""
    SELECT new com.example.gradproj.EduNest.dto.chat.MentorshipRoomDetailsResponse(
        cr.publicId,
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
