package com.example.gradproj.EduNest.service.notification;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.notification.NotificationDto;
import com.example.gradproj.EduNest.entity.notification.Notification;
import com.example.gradproj.EduNest.entity.notification.UserNotification;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.notification.NotificationRepository;
import com.example.gradproj.EduNest.repository.notification.UserNotificationRepository;
import com.example.gradproj.EduNest.repository.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepo;
    private final UserNotificationRepository userNotificationRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepo;
    private final EnrollmentRepository enrollmentRepo;


    private NotificationDto mapToDto(UserNotification u){

        Notification n = u.getNotification();

        return NotificationDto.builder()
                .id(u.getId())
                .title(n.getTitle())
                .content(n.getContent())
                .type(n.getType())
                .isRead(u.isRead())
                .time(n.getCreatedAt())
                .build();
    }

    public void sendToUser(
            String email,
            String title,
            String content,
            NotificationType type
    ){

        UserEntity user =
                userRepo.getReferenceById(
                        userRepo.findIdByEmail(email)
                                .orElseThrow(() ->
                                        new UsernameNotFoundException("User not found"))
                );

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

        messagingTemplate.convertAndSendToUser(
                email,
                "/queue/notifications",
                mapToDto(relation)
        );
    }

//    public void sendToUsers(
//            List<UserEntity> users,
//            String title,
//            String content,
//            NotificationType type
//    ){
//
//        Notification notification =
//                Notification.builder()
//                        .title(title)
//                        .content(content)
//                        .type(type)
//                        .build();
//
//        notificationRepo.save(notification);
//
//        List<UserNotification> relations =
//                users.stream()
//                        .map(user ->
//                                UserNotification.builder()
//                                        .user(user)
//                                        .notification(notification)
//                                        .isRead(false)
//                                        .build()
//                        )
//                        .toList();
//
//        userNotificationRepo.saveAll(relations);
//
//        for(int i = 0; i < relations.size(); i++){
//
//            messagingTemplate.convertAndSendToUser(
//                    users.get(i).getEmail(),
//                    "/queue/notifications",
//                    mapToDto(relations.get(i))
//            );
//        }
//    }

    @Transactional
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

            messagingTemplate.convertAndSendToUser(
                    relation.getUser().getEmail(),
                    "/queue/notifications",
                    mapToDto(relation)
            );
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> getUserNotifications(
            String email,
            int size,
            int page
    ){

        Pageable pageable = PageRequest.of(page, size);

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

        Pageable pageable = PageRequest.of(page, size);

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

    public void markOneAsRead(Long relationId){
        userNotificationRepo.markOneAsRead(relationId);
    }

    public void deleteNotification(Long relationId){
        userNotificationRepo.deleteById(relationId);
    }

    public void deleteAllNotificationsForUser(String email){
        userNotificationRepo.deleteAllByUserEmail(email);
    }
}