package com.example.gradproj.EduNest.service.notification;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.notification.AdminNotificationResponse;
import com.example.gradproj.EduNest.dto.notification.NotificationDto;
import com.example.gradproj.EduNest.entity.notification.AdminNotification;
import com.example.gradproj.EduNest.entity.notification.Notification;
import com.example.gradproj.EduNest.entity.notification.UserNotification;
import com.example.gradproj.EduNest.entity.users.Admin;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.notification.AdminNotificationRepository;
import com.example.gradproj.EduNest.repository.notification.NotificationRepository;
import com.example.gradproj.EduNest.repository.notification.UserNotificationRepository;
import com.example.gradproj.EduNest.repository.users.AdminRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import com.example.gradproj.EduNest.service.security.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.gradproj.EduNest.config.transaction.AfterCommitExecutor;


import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepo;
    private final UserNotificationRepository userNotificationRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final AdminRepository adminRepo;
    private final AdminNotificationRepository adminNotificationRepo;
    private final SecurityService securityService;
    private final AfterCommitExecutor afterCommitExecutor;


    private NotificationDto mapToDto(UserNotification u){

        Notification n = u.getNotification();

        return NotificationDto.builder()
                .id(u.getPublicId())
                .title(n.getTitle())
                .content(n.getContent())
                .type(n.getType())
                .isRead(u.isRead())
                .time(n.getCreatedAt())
                .build();
    }

    private void sendAfterCommit(Runnable sendAction) {
        afterCommitExecutor.execute(sendAction);
    }

    private int clampPageSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }


    public boolean sendToUserByEmailIfExists(
            String email,
            String title,
            String content,
            NotificationType type
    ) {
        var userIdOpt = userRepo.findIdByEmail(email);
        if (userIdOpt.isEmpty()) {
            return false;
        }

        UserEntity user = userRepo.getReferenceById(userIdOpt.get());

        Notification notification =
                Notification.builder()
                        .title(title)
                        .content(content)
                        .type(type)
                        .build();

        notificationRepo.save(notification);

        UserNotification relation =
                UserNotification.builder()
                        .user(user)
                        .notification(notification)
                        .isRead(false)
                        .build();

        userNotificationRepo.save(relation);

        NotificationDto dto = mapToDto(relation);
        sendAfterCommit(() -> messagingTemplate.convertAndSendToUser(
                email,
                "/queue/notifications",
                dto
        ));
        return true;
    }

    public void sendToUserByEmail(
            String email,
            String title,
            String content,
            NotificationType type
    ){
        boolean sent = sendToUserByEmailIfExists(email, title, content, type);
        if (!sent) {
            throw new UsernameNotFoundException("User not found with email: " + email);
        }
    }

    @Transactional
    @Async
    //ToDo : maybe add mentor of the mentorship who can send better
    public void sendToMentorshipStudents(
            Long mentorshipId,
            String title,
            String content,
            NotificationType type
    ) {

        List<Student> students =
                enrollmentRepo.findStudentsByMentorshipId(mentorshipId);

        Notification notification =
                Notification.builder()
                        .title(title)
                        .content(content)
                        .type(type)
                        .build();

        notificationRepo.save(notification);

        List<UserNotification> relations =
                students.stream()
                        .map(student ->
                                UserNotification.builder()
                                        .user(student)
                                        .notification(notification)
                                        .isRead(false)
                                        .build()
                        )
                        .toList();

        userNotificationRepo.saveAll(relations);

        for (UserNotification relation : relations) {
            NotificationDto dto = mapToDto(relation);
            String studentEmail = relation.getUser().getEmail();
            sendAfterCommit(() -> messagingTemplate.convertAndSendToUser(
                    studentEmail,
                    "/queue/notifications",
                    dto
            ));
        }
    }

    @Transactional
    @Async
    public void sendToAdmin(String title, String content, NotificationType type){
        List<Admin> admins = adminRepo.findAll();
        if (admins == null || admins.isEmpty()) {
            return;
        }

        List<AdminNotification> adminNotifications = new java.util.ArrayList<>();
        for (Admin admin : admins) {
            adminNotifications.add(AdminNotification.builder()
                    .title(title)
                    .content(content)
                    .type(type)
                    .admin(admin)
                    .build());
        }

        List<AdminNotification> saved = adminNotificationRepo.saveAll(adminNotifications);

        for (int i = 0; i < admins.size(); i++) {
            Admin admin = admins.get(i);
            AdminNotification record = saved.get(i);
            AdminNotificationResponse payload = AdminNotificationResponse.builder()
                    .id(record.getPublicId())
                    .title(record.getTitle())
                    .content(record.getContent())
                    .type(record.getType())
                    .createdAt(record.getCreatedAt())
                    .build();

            sendAfterCommit(() -> messagingTemplate.convertAndSendToUser(
                    admin.getEmail(),
                    "/queue/notifications",
                    payload
            ));
        }
    }

    public PageResponse<AdminNotificationResponse> getAdminNotifications(int size, int page){

        Pageable pageable = PageRequest.of(page, clampPageSize(size));

        Page<AdminNotification> response =
                adminNotificationRepo.findAllByOrderByCreatedAtDesc(pageable);

        var notifications = response.stream().map(n ->
                AdminNotificationResponse.builder()
                        .id(n.getPublicId())
                        .title(n.getTitle())
                        .content(n.getContent())
                        .type(n.getType())
                        .createdAt(n.getCreatedAt())
                        .build()).toList();

        return PageResponse.<AdminNotificationResponse>builder()
                .content(notifications)
                .page(response.getNumber())
                .size(response.getSize())
                .totalElements(response.getTotalElements())
                .totalPages(response.getTotalPages())
                .build();
    }

    public void deleteAdminNotification(Long id){
        adminNotificationRepo.deleteById(id);
    }

    public void deleteAllAdminNotifications(){
        adminNotificationRepo.deleteAll();
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> getUserNotifications(
            String email,
            int size,
            int page
    ){

        Pageable pageable = PageRequest.of(page, clampPageSize(size));

        Page<UserNotification> response =
                userNotificationRepo
                        .findWithNotification(email, pageable);

        List<NotificationDto> content =
                response.getContent()
                        .stream()
                        .map(this::mapToDto)
                        .toList();

        return PageResponse.<NotificationDto>builder()
                .content(content)
                .totalElements(response.getTotalElements())
                .page(response.getNumber())
                .size(response.getSize())
                .totalPages(response.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String email){

        return userNotificationRepo
                .countByUserEmailAndIsReadFalse(email);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> getUnreadNotificationsForUser(
            String email,
            int size,
            int page
    ){

        Pageable pageable = PageRequest.of(page, clampPageSize(size));

        Page<UserNotification> response =
                userNotificationRepo
                        .findUnreadWithNotification(email, pageable);

        List<NotificationDto> content =
                response.getContent()
                        .stream()
                        .map(this::mapToDto)
                        .toList();

        return PageResponse.<NotificationDto>builder()
                .content(content)
                .page(response.getNumber())
                .size(response.getSize())
                .totalElements(response.getTotalElements())
                .totalPages(response.getTotalPages())
                .build();
    }

    /**
     * actions
     */
    public void markAllAsRead(String email){
        userNotificationRepo.markAllAsRead(email);
    }

    public void markOneAsRead(Long relationId,String email){
        if(!securityService.isUserOwnNotification(relationId, email))
            throw new AccessDeniedException("You are not the owner of this notification");

        userNotificationRepo.markOneAsRead(relationId);
    }

    public void deleteNotification(Long relationId,String email){
        if(!securityService.isUserOwnNotification(relationId, email))
            throw new AccessDeniedException("You are not the owner of this notification");

        userNotificationRepo.deleteById(relationId);
    }

    public void deleteAllNotificationsForUser(String email){
        userNotificationRepo.deleteAllByUserEmail(email);
    }

    public void markOneAsRead(UUID relationPublicId, String email){
        if(!securityService.isUserOwnNotification(relationPublicId, email))
            throw new AccessDeniedException("You are not the owner of this notification");

        if (relationPublicId != null && relationPublicId.getMostSignificantBits() == 0L) {
            userNotificationRepo.markOneAsRead(relationPublicId.getLeastSignificantBits());
        } else {
            userNotificationRepo.markOneAsReadByPublicId(relationPublicId);
        }
    }

    public void deleteNotification(UUID relationPublicId, String email){
        if(!securityService.isUserOwnNotification(relationPublicId, email))
            throw new AccessDeniedException("You are not the owner of this notification");

        if (relationPublicId != null && relationPublicId.getMostSignificantBits() == 0L) {
            userNotificationRepo.deleteById(relationPublicId.getLeastSignificantBits());
        } else {
            userNotificationRepo.deleteByPublicId(relationPublicId);
        }
    }

    public void deleteAdminNotification(UUID id){
        adminNotificationRepo.deleteByPublicId(id);
    }

}
