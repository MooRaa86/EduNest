package com.example.gradproj.EduNest.repository.chat;

import com.example.gradproj.EduNest.entity.chat.ChatMessage;
import com.example.gradproj.EduNest.repository.chat.projection.ChatMessageProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {

    @Query("""
    select
        m.id as messageId,
        m.content as content,
        m.createdAt as createdAt,
        m.senderEmail as senderEmail,
        m.senderName as senderName,
        m.sender.profileImageUrl as senderProfileImageUrl
    from ChatMessage m
    where m.chatRoom.id = :roomId
      and (:beforeId is null or m.id < :beforeId)
    order by m.id desc
""")
    List<ChatMessageProjection> findMessagesByRoom(
            Long roomId,
            Long beforeId,
            Pageable pageable
    );

    @Modifying
    @Query("""
    DELETE FROM ChatMessage m
    WHERE m.id = :messageId
      AND m.sender.email = :senderEmail
""")
    int deleteByIdAndSender(Long messageId, String senderEmail);

}
