package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.entity.chat.ChatMessage;
import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.entity.chat.ChatRoomMember;
import com.example.gradproj.EduNest.entity.chat.Conversation;
import com.example.gradproj.EduNest.entity.chat.Message;
import com.example.gradproj.EduNest.entity.mentorship.MentorShipReviews;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.repository.chat.ChatMessageRepository;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.repository.chat.ConversationRepository;
import com.example.gradproj.EduNest.repository.chat.MembersRepo;
import com.example.gradproj.EduNest.repository.chat.MessageRepository;
import com.example.gradproj.EduNest.repository.mentorShip.ReviewsRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class SoftDeleteReadPolicyPostgresTest extends ProdChainIntegrationTestBase {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.flyway.enabled", () -> true);
        r.add("spring.flyway.placeholders.defaultCommissionRate", () -> "10.0");
        r.add("app.scheduling.enabled", () -> false);
        r.add("app.completion.worker.enabled", () -> true);
    }

    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired TotalPointsRepository points;
    @Autowired ReviewsRepository reviews;
    @Autowired UserRepository users;
    @Autowired ChatRoomRepository rooms;
    @Autowired ChatMessageRepository roomMessages;
    @Autowired MembersRepo members;
    @Autowired ConversationRepository conversations;
    @Autowired MessageRepository messages;
    @Autowired ObjectMapper json;
    @Autowired com.example.gradproj.EduNest.service.chat.ConversationService conversationService;
    @Autowired com.example.gradproj.EduNest.service.mentorShip.mentorShipService mentorshipService;
    @Autowired com.example.gradproj.EduNest.service.completion.CompletionWorker completionWorker;

    @Test
    void leaderboardAndReviewsExcludeDeletedButRetainDisabledAndHistoricalRows() throws Exception {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb46-owner-" + suffix + "@example.org", Status.ACTIVE);
        var deleted = auth.enroll(chain.mentorship().getId(), "eb46-deleted-" + suffix + "@example.org").getStudent();
        var first = auth.enroll(chain.mentorship().getId(), "eb46-first-" + suffix + "@example.org").getStudent();
        var second = auth.enroll(chain.mentorship().getId(), "eb46-second-" + suffix + "@example.org").getStudent();
        var third = auth.enroll(chain.mentorship().getId(), "eb46-third-" + suffix + "@example.org").getStudent();
        for (var student : java.util.List.of(deleted, first, second, third)) {
            points.saveAndFlush(TotalPoints.builder().student(student).mentorship(chain.mentorship()).totalPoints(10).build());
            reviews.saveAndFlush(MentorShipReviews.builder().student(student).mentorShip(chain.mentorship()).rating(student.getId().equals(deleted.getId()) ? 1 : 5).feedBack(student.getEmail()).build());
        }
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(chain.mentor().getEmail(), null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MENTOR"))));
        try { mentorshipService.updateMentorShipStatus(chain.mentorship().getPublicId(), Status.COMPLETED); }
        finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        completionWorker.poll();
        completionWorker.poll();
        long frozenRank = jdbc.queryForObject("SELECT student_rank FROM certificates WHERE mentorship_id=? AND student_id=?", Long.class, chain.mentorship().getId(), first.getId());
        assertThat(frozenRank).isEqualTo(2);
        jdbc.update("UPDATE users SET deleted=true WHERE id=?", deleted.getId());
        jdbc.update("UPDATE users SET enabled=false WHERE id=?", second.getId());
        var page = points.findLeaderboardByMentorshipId(chain.mentorship().getId(), PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent().get(0).getStudentEmail()).isEqualTo(first.getEmail());
        assertThat(points.findLeaderboardByMentorshipId(chain.mentorship().getId(), PageRequest.of(1, 1)).getContent().get(0).getStudentEmail()).isEqualTo(second.getEmail());
        assertThat(points.findStudentInLeaderboard(chain.mentorship().getId(), second.getEmail()).orElseThrow().getRank()).isEqualTo(2);
        String leaderboardBody = mockMvc.perform(get("/api/v1/student/mentorships/{id}/leaderboard", chain.mentorship().getPublicId())
                .param("size", "1").param("page", "0").header("Authorization", auth.authToken(third.getEmail(), AuthFixture.STUDENT)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var board = json.readTree(leaderboardBody).path("apiResponse").path("leaderboard");
        assertThat(board.path("userRank").asLong()).isEqualTo(3);
        assertThat(board.path("leaderboard").path("totalElements").asLong()).isEqualTo(3);
        assertThat(leaderboardBody).doesNotContain(deleted.getEmail());
        assertThat(reviews.findMentorshipReviews(chain.mentorship().getId(), null, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(3);
        assertThat(reviews.calculateVisibleAverageRating(chain.mentorship().getId())).isEqualTo(5.0);
        assertThat(reviews.findReviewsByMentorPublicId(chain.mentor().getPublicId(), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(3);
        assertThat(points.findByMentorship_Id(chain.mentorship().getId())).hasSize(4);
        assertThat(reviews.findByMentorShipId(chain.mentorship().getId())).hasSize(4);
        assertThat(users.findAllUsers(PageRequest.of(0, 100)).getContent()).anyMatch(u -> u.getId().equals(deleted.getPublicId()));
        assertThat(jdbc.queryForObject("SELECT student_rank FROM certificates WHERE mentorship_id=? AND student_id=?", Long.class, chain.mentorship().getId(), first.getId())).isEqualTo(frozenRank);
        assertThat(jdbc.queryForObject("SELECT s.student_rank FROM mentorship_completion_snapshot s JOIN mentorship_completion_job j ON j.id=s.job_id WHERE j.mentorship_id=? AND s.student_id=?", Long.class, chain.mentorship().getId(), first.getId())).isEqualTo(frozenRank);
    }

    @Test
    void chatHistoryRedactsDeletedIdentityWithoutErasingMessagesOrChangingAccess() throws Exception {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb46-room-owner-" + suffix + "@example.org", Status.ACTIVE);
        var reader = auth.enroll(chain.mentorship().getId(), "eb46-room-reader-" + suffix + "@example.org").getStudent();
        var deleted = auth.enroll(chain.mentorship().getId(), "eb46-room-deleted-" + suffix + "@example.org").getStudent();
        var outsider = auth.student("eb46-room-outsider-" + suffix + "@example.org");
        var admin = auth.admin("eb46-admin-" + suffix + "@example.org");
        var room = rooms.saveAndFlush(ChatRoom.builder().name("EB46").mentorship(chain.mentorship()).creator(chain.mentor()).build());
        members.saveAndFlush(ChatRoomMember.builder().chatRoom(room).user(deleted).joinedAt(LocalDateTime.now()).build());
        var roomMessage = roomMessages.saveAndFlush(ChatMessage.builder().chatRoom(room).sender(deleted).senderEmail(deleted.getEmail())
            .senderName("Private Sender").content("Room history retained").build());
        var conversation = conversations.saveAndFlush(Conversation.builder().user1(reader).user2(deleted).createdAt(LocalDateTime.now()).build());
        var privateMessage = messages.saveAndFlush(Message.builder().conversation(conversation).sender(deleted).content("Private history retained").sentAt(LocalDateTime.now()).build());
        jdbc.update("UPDATE users SET deleted=true, profile_image_url='https://example.org/private.png' WHERE id=?", deleted.getId());
        String token = auth.authToken(reader.getEmail(), AuthFixture.STUDENT);
        String roomBody = mockMvc.perform(get("/api/v1/chat-room/{id}/messages", room.getPublicId()).header("Authorization", token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(roomBody).contains("Room history retained", "Deleted user", roomMessage.getPublicId().toString())
            .doesNotContain(deleted.getEmail(), "Private Sender", "https://example.org/private.png", deleted.getPublicId().toString());
        String membersBody = mockMvc.perform(get("/api/v1/chat-room/{id}/members", room.getPublicId()).header("Authorization", token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(membersBody).contains("Deleted user").doesNotContain(deleted.getEmail(), deleted.getPublicId().toString(), "https://example.org/private.png");
        mockMvc.perform(get("/api/v1/chat-room/{id}/messages", room.getPublicId())
            .header("Authorization", auth.authToken(outsider.getEmail(), AuthFixture.STUDENT))).andExpect(status().isForbidden());
        String privateBody = mockMvc.perform(get("/api/v1/conversation/{id}/messages", conversation.getPublicId()).header("Authorization", token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(privateBody).contains("Private history retained", "Deleted user", privateMessage.getId().toString())
            .doesNotContain(deleted.getEmail(), deleted.getPublicId().toString(), "https://example.org/private.png");
        mockMvc.perform(get("/api/v1/conversation/{id}/messages", conversation.getPublicId())
            .header("Authorization", auth.authToken(outsider.getEmail(), AuthFixture.STUDENT))).andExpect(status().isForbidden());
        String adminBody = mockMvc.perform(get("/api/v1/conversation/{id}/messages", conversation.getPublicId())
            .header("Authorization", auth.authToken(admin.getEmail(), AuthFixture.ADMIN))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(adminBody).contains(deleted.getEmail());
        assertThat(messages.findById(privateMessage.getId())).isPresent();
        assertThat(roomMessages.findById(roomMessage.getId())).isPresent();
    }

    @Test
    void privateDeliveryUsesPersistedRecipientAndRedactsDeletedIdentity() throws Exception {
        String suffix = UUID.randomUUID().toString();
        var sender = auth.student("eb46-sender-" + suffix + "@example.org");
        var recipient = auth.student("eb46-recipient-" + suffix + "@example.org");
        var attacker = auth.student("eb46-attacker-" + suffix + "@example.org");
        var outbound = org.mockito.Mockito.mock(org.springframework.messaging.simp.SimpMessagingTemplate.class);
        var controller = new com.example.gradproj.EduNest.controller.chat.ConversationWebSocketControllers(conversationService, outbound);
        var request = com.example.gradproj.EduNest.dto.chat.ConversationMessageRequest.builder()
            .recipientId(recipient.getPublicId()).recipientEmail(attacker.getEmail()).content("Secure delivery").build();
        controller.sendPrivate(request, () -> sender.getEmail());
        org.mockito.Mockito.verify(outbound).convertAndSendToUser(org.mockito.ArgumentMatchers.eq(recipient.getEmail()),
            org.mockito.ArgumentMatchers.eq("/queue/messages"), org.mockito.ArgumentMatchers.any(com.example.gradproj.EduNest.dto.chat.ConversationMessageResponse.class));
        org.mockito.Mockito.verify(outbound, org.mockito.Mockito.never()).convertAndSendToUser(org.mockito.ArgumentMatchers.eq(attacker.getEmail()),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        jdbc.update("UPDATE users SET deleted=true, profile_image_url='https://example.org/deleted.png' WHERE id=?", recipient.getId());
        var sent = conversationService.sendMessage(sender.getEmail(), request);
        assertThat(sent.getRecipientEmail()).isNull();
        assertThat(sent.getRecipientId()).isNull();
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/conversation/messages/{id}", sent.getId())
                .header("Authorization", auth.authToken(sender.getEmail(), AuthFixture.STUDENT)).contentType("application/json")
                .content("{\"content\":\"Edited safely\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Edited safely").doesNotContain(recipient.getEmail(), recipient.getPublicId().toString(), "https://example.org/deleted.png");
        org.mockito.Mockito.clearInvocations(outbound);
        controller.sendPrivate(request, () -> sender.getEmail());
        var payload = org.mockito.ArgumentCaptor.forClass(com.example.gradproj.EduNest.dto.chat.ConversationMessageResponse.class);
        org.mockito.Mockito.verify(outbound).convertAndSendToUser(org.mockito.ArgumentMatchers.eq(sender.getEmail()),
            org.mockito.ArgumentMatchers.eq("/queue/messages"), payload.capture());
        assertThat(payload.getValue().getRecipientEmail()).isNull();
        assertThat(payload.getValue().getRecipientId()).isNull();
        org.mockito.Mockito.verify(outbound, org.mockito.Mockito.never()).convertAndSendToUser(org.mockito.ArgumentMatchers.eq(attacker.getEmail()),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deletedMentorIsHiddenFromStudentDiscoveryAndDetailButNotAdminDetail() throws Exception {
        String suffix = UUID.randomUUID().toString();
        var chain = auth.mentorship("eb46-deleted-mentor-" + suffix + "@example.org", Status.ACTIVE);
        var student = auth.student("eb46-discovery-student-" + suffix + "@example.org");
        var admin = auth.admin("eb46-discovery-admin-" + suffix + "@example.org");
        jdbc.update("UPDATE users SET deleted=true WHERE id=?", chain.mentor().getId());
        String studentToken = auth.authToken(student.getEmail(), AuthFixture.STUDENT);
        String adminToken = auth.authToken(admin.getEmail(), AuthFixture.ADMIN);
        mockMvc.perform(get("/api/v1/mentorship/{id}", chain.mentorship().getPublicId()).header("Authorization", studentToken))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/mentorship/{id}", chain.mentorship().getPublicId()).header("Authorization", adminToken))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/student/mentorships/{id}/overview", chain.mentorship().getPublicId()).header("Authorization", studentToken))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/profile/mentor/{id}", chain.mentor().getPublicId()).header("Authorization", studentToken))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/profile/mentor/{id}", chain.mentor().getPublicId()).header("Authorization", adminToken))
            .andExpect(status().isOk());
        String list = mockMvc.perform(get("/api/v1/mentorship").header("Authorization", studentToken))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(chain.mentorship().getPublicId().toString());
    }
}
