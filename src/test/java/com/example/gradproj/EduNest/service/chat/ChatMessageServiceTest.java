package com.example.gradproj.EduNest.service.chat;

import com.example.gradproj.EduNest.config.webSocket.ChatPrincipal;
import com.example.gradproj.EduNest.dto.chat.ChatMessageResponse;
import com.example.gradproj.EduNest.entity.chat.ChatMessage;
import com.example.gradproj.EduNest.entity.chat.ChatRoom;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.chat.ChatMessageRepository;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.service.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EB-55 / CHAT-01 & CHAT-06: ChatMessageService Reliability & Room Boundary Enforcement")
class ChatMessageServiceTest {

    @Mock
    private ChatMessageRepository messageRepo;
    @Mock
    private ChatRoomRepository roomRepo;
    @Mock
    private UserRepository userRepo;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private SecurityService securityService;
    @Mock
    private ChatAuthorization chatAuthorization;

    private ChatMessageService chatMessageService;

    @BeforeEach
    void setUp() {
        chatMessageService = new ChatMessageService(
                messageRepo,
                roomRepo,
                userRepo,
                messagingTemplate,
                securityService,
                chatAuthorization
        );
    }

    @Nested
    @DisplayName("saveMessage Reliability (CHAT-01)")
    class SaveMessageTests {

        private final ChatPrincipal principal = new ChatPrincipal("alice@example.com", "Alice");

        @Test
        @DisplayName("Rejects null or blank content with globalLogicEx")
        void rejectsBlankContent() {
            assertThatThrownBy(() -> chatMessageService.saveMessage(1L, "  ", principal))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("When room does not exist, sends error to /queue/errors and returns null")
        void roomNotFound_sendsErrorAndReturnsNull() {
            when(roomRepo.existsById(99L)).thenReturn(false);

            ChatMessageResponse res = chatMessageService.saveMessage(99L, "hello", principal);

            assertThat(res).isNull();
            verify(messagingTemplate).convertAndSendToUser("alice@example.com", "/queue/errors", "Room not found with this id");
            verify(messageRepo, never()).save(any());
        }

        @Test
        @DisplayName("When sender is not a room member, drops message, sends forbidden error, and returns null")
        void nonMember_sendsErrorAndReturnsNull() {
            when(roomRepo.existsById(1L)).thenReturn(true);
            when(chatAuthorization.canWriteRoom(1L, "alice@example.com")).thenReturn(false);

            ChatMessageResponse res = chatMessageService.saveMessage(1L, "hello", principal);

            assertThat(res).isNull();
            verify(messagingTemplate).convertAndSendToUser("alice@example.com", "/queue/errors", "Forbidden: you are not a member of this room");
            verify(messageRepo, never()).save(any());
        }

        @Test
        @DisplayName("When sender user cannot be found in DB, sends error and returns null")
        void userNotFound_sendsErrorAndReturnsNull() {
            when(roomRepo.existsById(1L)).thenReturn(true);
            when(chatAuthorization.canWriteRoom(1L, "alice@example.com")).thenReturn(true);
            when(roomRepo.getReferenceById(1L)).thenReturn(ChatRoom.builder().build());
            when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.empty());

            ChatMessageResponse res = chatMessageService.saveMessage(1L, "hello", principal);

            assertThat(res).isNull();
            verify(messagingTemplate).convertAndSendToUser("alice@example.com", "/queue/errors", "User not found");
            verify(messageRepo, never()).save(any());
        }

        @Test
        @DisplayName("When database persistence fails, catches exception, logs, sends error to /queue/errors, and returns null (preventing null broadcast)")
        void databaseFailure_sendsErrorAndReturnsNull() {
            when(roomRepo.existsById(1L)).thenReturn(true);
            when(chatAuthorization.canWriteRoom(1L, "alice@example.com")).thenReturn(true);
            ChatRoom room = ChatRoom.builder().build();
            when(roomRepo.getReferenceById(1L)).thenReturn(room);

            UserEntity sender = Student.builder().email("alice@example.com").build();
            when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(sender));

            when(messageRepo.save(any(ChatMessage.class)))
                    .thenThrow(new DataIntegrityViolationException("Database constraint violation"));

            ChatMessageResponse res = chatMessageService.saveMessage(1L, "hello", principal);

            assertThat(res).isNull();
            verify(messagingTemplate).convertAndSendToUser(
                    eq("alice@example.com"),
                    eq("/queue/errors"),
                    eq("Failed to save message. Please try again.")
            );
        }

        @Test
        @DisplayName("When save succeeds, returns ChatMessageResponse")
        void saveSuccess_returnsResponse() {
            when(roomRepo.existsById(1L)).thenReturn(true);
            when(chatAuthorization.canWriteRoom(1L, "alice@example.com")).thenReturn(true);
            ChatRoom room = ChatRoom.builder().build();
            when(roomRepo.getReferenceById(1L)).thenReturn(room);

            UserEntity sender = Student.builder().email("alice@example.com").build();
            when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(sender));

            ChatMessage savedMsg = ChatMessage.builder()
                    .content("hello")
                    .chatRoom(room)
                    .sender(sender)
                    .senderEmail("alice@example.com")
                    .senderName("Alice")
                    .build();
            when(messageRepo.save(any(ChatMessage.class))).thenReturn(savedMsg);

            ChatMessageResponse res = chatMessageService.saveMessage(1L, "hello", principal);

            assertThat(res).isNotNull();
            assertThat(res.getMessage()).isEqualTo("hello");
            assertThat(res.getRoomId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("editMessage Validation & Room Guard (CHAT-06)")
    class EditMessageTests {

        private ChatRoom room1;
        private ChatMessage message1;

        @BeforeEach
        void initMessage() {
            room1 = ChatRoom.builder().build();
            room1.setId(10L);

            message1 = ChatMessage.builder()
                    .content("old text")
                    .chatRoom(room1)
                    .senderEmail("alice@example.com")
                    .senderName("Alice")
                    .build();
            message1.setId(500L);
        }

        @Test
        @DisplayName("Rejects blank content with globalLogicEx")
        void blankContent_throws() {
            assertThatThrownBy(() -> chatMessageService.editMessage(10L, 500L, "alice@example.com", "   "))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("When message does not exist, throws globalLogicEx")
        void messageNotFound_throws() {
            when(messageRepo.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> chatMessageService.editMessage(10L, 999L, "alice@example.com", "new text"))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessageContaining("Message not found");
        }

        @Test
        @DisplayName("When roomId does not match message's genuine chatRoom, throws globalLogicEx (CHAT-06 spoofing defense)")
        void roomMismatch_throws() {
            when(messageRepo.findById(500L)).thenReturn(Optional.of(message1));

            // Passed roomId = 20L, but message belongs to room 10L
            assertThatThrownBy(() -> chatMessageService.editMessage(20L, 500L, "alice@example.com", "new text"))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessageContaining("Message does not belong to this room");
        }

        @Test
        @DisplayName("When senderEmail does not match message sender, throws globalLogicEx")
        void senderMismatch_throwsGlobalLogicEx() {
            when(messageRepo.findById(500L)).thenReturn(Optional.of(message1));

            // Called by eve@example.com instead of alice@example.com
            assertThatThrownBy(() -> chatMessageService.editMessage(10L, 500L, "eve@example.com", "new text"))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessageContaining("Not allowed to edit this message");
        }

        @Test
        @DisplayName("When roomId, messageId, and sender match, updates message and returns response")
        void validEdit_updatesAndReturns() {
            when(messageRepo.findById(500L)).thenReturn(Optional.of(message1));

            ChatMessageResponse response = chatMessageService.editMessage(10L, 500L, "alice@example.com", "new text");

            assertThat(response).isNotNull();
            assertThat(response.getMessage()).isEqualTo("new text");
            assertThat(response.getRoomId()).isEqualTo(10L);
            verify(messageRepo).save(message1);
        }
    }

    @Nested
    @DisplayName("deleteMessage Validation & Room Guard (CHAT-06)")
    class DeleteMessageTests {

        private ChatRoom room1;
        private ChatMessage message1;

        @BeforeEach
        void initMessage() {
            room1 = ChatRoom.builder().build();
            room1.setId(10L);

            message1 = ChatMessage.builder()
                    .content("to be deleted")
                    .chatRoom(room1)
                    .senderEmail("alice@example.com")
                    .senderName("Alice")
                    .build();
            message1.setId(500L);
        }

        @Test
        @DisplayName("When message does not exist, throws globalLogicEx")
        void messageNotFound_throws() {
            when(messageRepo.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> chatMessageService.deleteMessage(10L, 999L, "alice@example.com"))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessageContaining("Message not found");
        }

        @Test
        @DisplayName("When roomId does not match message's genuine chatRoom, throws globalLogicEx (CHAT-06 spoofing defense)")
        void roomMismatch_throws() {
            when(messageRepo.findById(500L)).thenReturn(Optional.of(message1));

            // Passed roomId = 20L, but message belongs to room 10L
            assertThatThrownBy(() -> chatMessageService.deleteMessage(20L, 500L, "alice@example.com"))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessageContaining("Message does not belong to this room");
            verify(messageRepo, never()).delete(any());
        }

        @Test
        @DisplayName("When senderEmail does not match message sender, throws globalLogicEx")
        void senderMismatch_throwsGlobalLogicEx() {
            when(messageRepo.findById(500L)).thenReturn(Optional.of(message1));

            // Called by eve@example.com instead of alice@example.com
            assertThatThrownBy(() -> chatMessageService.deleteMessage(10L, 500L, "eve@example.com"))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessageContaining("Not allowed to delete this message");
            verify(messageRepo, never()).delete(any());
        }

        @Test
        @DisplayName("When roomId, messageId, and sender match, deletes entity and returns actual roomId")
        void validDelete_deletesAndReturnsActualRoomId() {
            when(messageRepo.findById(500L)).thenReturn(Optional.of(message1));

            Long actualRoomId = chatMessageService.deleteMessage(10L, 500L, "alice@example.com");

            assertThat(actualRoomId).isEqualTo(10L);
            verify(messageRepo).delete(message1);
        }
    }
}
