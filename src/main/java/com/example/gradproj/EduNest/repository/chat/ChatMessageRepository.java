package com.example.gradproj.EduNest.repository.chat;

import com.example.gradproj.EduNest.entity.chat.ChatMessage;
import com.example.gradproj.EduNest.repository.chat.projection.ChatMessageProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT e FROM ChatMessage e WHERE e.publicId = :publicId")
    Optional<ChatMessage> findByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<ChatMessage> findByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<ChatMessage> entity = findByPublicIdDirect(publicId);
        if (entity.isPresent()) return entity;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits());
        }
        return Optional.empty();
    }
    boolean existsByPublicId(UUID publicId);
    void deleteByPublicId(UUID publicId);

    @Query("SELECT m.id FROM ChatMessage m WHERE m.publicId = :publicId")
    Optional<Long> findIdByPublicIdDirect(@Param("publicId") UUID publicId);

    default Optional<Long> findIdByPublicId(UUID publicId) {
        if (publicId == null) return Optional.empty();
        Optional<Long> id = findIdByPublicIdDirect(publicId);
        if (id.isPresent()) return id;
        if (publicId.getMostSignificantBits() == 0L) {
            return findById(publicId.getLeastSignificantBits()).map(ChatMessage::getId);
        }
        return Optional.empty();
    }

    @Modifying
    @Query("""
    DELETE FROM ChatMessage m
    WHERE m.publicId = :messagePublicId
      AND m.sender.email = :senderEmail
""")
    int deleteByPublicIdAndSender(@Param("messagePublicId") UUID messagePublicId, @Param("senderEmail") String senderEmail);


    @Query("""
    select
        m.publicId as messageId,
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
