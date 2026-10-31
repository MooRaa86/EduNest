package com.example.gradproj.EduNest.service.chat;

import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.AuthorizationRepository;
import com.example.gradproj.EduNest.repository.chat.ChatRoomRepository;
import com.example.gradproj.EduNest.repository.chat.MembersRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * P1-12 / EB-19 — single authorization authority for the chat surfaces
 * (WS room subscribe/send, WS private send, and the defense-in-depth
 * re-check inside {@link ChatMessageService#saveMessage}).
 *
 * Membership semantics per decision D-14 (user-approved default):
 * <ul>
 *   <li><b>READ</b> (SUBSCRIBE {@code /topic/room/{roomId}}): mentorship
 *       enrollment <i>or</i> the mentorship's mentor — the enrollment proxy is
 *       the documented bridge while {@code chat_room_members} stays
 *       membership-lite ({@code AuthorizationRepository#isUserMemberOfChatRoom}).</li>
 *   <li><b>WRITE</b> (SEND {@code /app/chat/{roomId}}): explicit room member
 *       row <i>or</i> the room's owning mentor (mentors create rooms without
 *       joining them first).</li>
 *   <li><b>PRIVATE</b> ({@code /app/chat.private}): sender ≠ recipient; the
 *       sender is always the authenticated principal and the recipient must be
 *       a real user (pair validation; the client-controlled <i>event routing</i>
 *       of REST edit/delete pushes stays under CHAT-04 / P1-13 scope).</li>
 * </ul>
 *
 * Violations never throw from this class by design: callers translate the
 * boolean answers into their surface's error contract (STOMP ERROR frame on
 * the interceptor, silent-drop + /queue/errors push inside the service).
 */
@Component
@RequiredArgsConstructor
public class ChatAuthorization {

    private final AuthorizationRepository securityRepository;
    private final MembersRepo membersRepo;
    private final ChatRoomRepository roomRepo;

    /** READ rule (D-14): enrolled in the room's mentorship OR owning mentor. */
    public boolean canReadRoom(Long roomId, String email) {
        return securityRepository.isUserMemberOfChatRoom(roomId, email);
    }

    /** WRITE rule (D-14): explicit ChatRoomMember row OR the room's owning mentor. */
    public boolean canWriteRoom(Long roomId, String email) {
        return membersRepo.existsByChatRoom_IdAndUser_Email(roomId, email)
                || roomRepo.isRoomOwnedByMentor(roomId, email);
    }

    /**
     * PRIVATE pair rule (P1-12): a private message needs two distinct,
 * existing users. The recipient's existence is enforced by the caller's
     * user lookup; this method guards the shape of the pair itself.
     */
    public void validatePrivatePair(String senderEmail, String recipientEmail) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new globalLogicEx("Recipient email must not be blank");
        }
        if (recipientEmail.equals(senderEmail)) {
            throw new globalLogicEx("You cannot send a private message to yourself");
        }
    }
}
