package com.example.gradproj.EduNest.repository.chat;

import com.example.gradproj.EduNest.entity.chat.Message;
import com.example.gradproj.EduNest.repository.chat.projection.ConversationMessageProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MessageRepository
        extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderBySentAtDesc(
            Long conversationId,
            Pageable pageable
    );

    @Query("""
    SELECT
        m.id as messageId,
        m.content as content,
        m.sentAt as sentAt,
        m.sender.email as senderEmail,
        CONCAT(m.sender.firstName,' ',m.sender.lastName) as senderName,
        m.sender.profileImageUrl as senderProfileImageUrl,
        m.conversation.id as conversationId
    FROM Message m
    WHERE m.conversation.id = :conversationId
      AND (:beforeId IS NULL OR m.id < :beforeId)
    ORDER BY m.id DESC
""")
    List<ConversationMessageProjection> findConversationMessages(
            Long conversationId,
            Long beforeId,
            Pageable pageable
    );

    @Modifying
    @Query("""
    DELETE FROM Message m
    WHERE m.id = :messageId
      AND m.sender.email = :senderEmail
""")
    int deleteByIdAndSender(Long messageId, String senderEmail);

}