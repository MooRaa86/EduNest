package com.example.gradproj.EduNest.repository.chat;

import com.example.gradproj.EduNest.entity.chat.Conversation;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.repository.chat.projection.ConversationListProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {

    @Query("""
        SELECT c FROM Conversation c
        WHERE (c.user1.email = :email1 AND c.user2.email = :email2)
           OR (c.user1.email = :email2 AND c.user2.email = :email1)
    """)
    Optional<Conversation> findBetweenUsers(String email1, String email2);

    @Query("""
SELECT
 c.id as conversationId,

 CASE
   WHEN c.user1.email = :email THEN c.user2.email
   ELSE c.user1.email
 END as otherUserEmail,

 CASE
   WHEN c.user1.email = :email
   THEN CONCAT(c.user2.firstName,' ',c.user2.lastName)
   ELSE CONCAT(c.user1.firstName,' ',c.user1.lastName)
 END as otherUserName,

 m.content as lastMessage,
 m.sentAt as lastMessageTime

FROM Conversation c
LEFT JOIN Message m ON m.id = (
    SELECT MAX(mm.id)
    FROM Message mm
    WHERE mm.conversation.id = c.id
)

WHERE c.user1.email = :email
   OR c.user2.email = :email

ORDER BY m.sentAt DESC
""")
    List<ConversationListProjection> findUserConversations(String email);

    void deleteByUser1OrUser2(UserEntity user1, UserEntity user2);
}