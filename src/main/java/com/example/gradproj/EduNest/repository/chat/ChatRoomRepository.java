package com.example.gradproj.EduNest.repository.chat;

import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.repository.chat.projection.ChatRoomProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("""
    select
        cr.id as roomId,
        cr.name as roomName,
        m.id as mentorshipId,
        m.title as mentorshipName,
        c.email as creatorEmail,
        concat(c.firstName, ' ', c.lastName) as creatorName
    from ChatRoom cr
    join cr.mentorship m
    join cr.creator c
    where m.id = :mentorshipId
""")
    List<ChatRoomProjection> findRoomsByMentorship(Long mentorshipId);

    @Query("""
   SELECT r
   FROM ChatRoom r
   WHERE r.id = :id
""")
    Optional<ChatRoom> findRoomById(Long id);



}
