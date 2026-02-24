package com.example.gradproj.EduNest.service.notification;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.notification.NotificationDto;
import com.example.gradproj.EduNest.entity.notification.Notification;
import com.example.gradproj.EduNest.entity.users.UserEntity;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.repository.notification.NotificationRepository;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepo;

    private NotificationDto mapToResponse(Notification n){
        return NotificationDto.builder()
                .id(n.getId())
                .title(n.getTitle())
                .content(n.getContent())
                .isRead(n.isRead())
                .type(n.getType())
                .build();
    }

    public void sendToUser(
            String email,
            String title,
            String content,
            NotificationType type
    ){

        Notification notification =
                Notification.builder()
                        .title(title)
                        .content(content)
                        .type(type)
                        .user(userRepo.getReferenceById(userRepo.findIdByEmail(email).orElseThrow(
                                () -> new UsernameNotFoundException("User not found")
                        )))
                        .isRead(false)
                        .build();

        notificationRepo.save(notification);

        NotificationDto dto = mapToResponse(notification);

        messagingTemplate.convertAndSendToUser(
                email,
                "/queue/notifications",
                dto
        );

    }

    public void sendToUsers(
            List<UserEntity> users,
            String title,
            String content,
            NotificationType type
    ){

        List<Notification> notifications =
                users.stream()
                        .map(user ->
                                Notification.builder()
                                        .title(title)
                                        .content(content)
                                        .type(type)
                                        .user(user)
                                        .isRead(false)
                                        .build()
                        )
                        .collect(Collectors.toList());

        notificationRepo.saveAll(notifications);


        for (Notification notification : notifications){

            NotificationDto dto = mapToResponse(notification);

            String email = notification.getUser().getEmail();

            messagingTemplate.convertAndSendToUser(
                    email,
                    "/queue/notifications",
                    dto
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

        Page<Notification> response = notificationRepo
                .findByUserEmailOrderByCreatedAtDesc(email,pageable);

        List<NotificationDto> notifications =
                        response.getContent()
                        .stream()
                        .map(this::mapToResponse)
                .toList();

        return PageResponse.<NotificationDto>builder()
                .content(notifications)
                .totalElements(response.getTotalElements())
                .page(response.getNumber())
                .size(response.getSize())
                .totalPages(response.getTotalPages())
                .build();

    }


    @Transactional(readOnly = true)
    public long getUnreadCount(String email){

        return notificationRepo
                .countByUserEmailAndIsReadFalse(email);
    }

    public PageResponse<NotificationDto> getUnreadNotificationsForUser(
            String email,
            int size,
            int page
    ){
        Pageable pageable = PageRequest.of(page, size);

        Page<Notification> response =
                notificationRepo
                        .findByUserEmailAndIsReadFalseOrderByCreatedAtDesc(email,pageable);

        List<NotificationDto> notifications =
                response.getContent()
                        .stream().map(this::mapToResponse)
                .toList();

        return PageResponse.<NotificationDto>builder()
                .content(notifications)
                .page(response.getNumber())
                .size(response.getSize())
                .totalElements(response.getTotalElements())
                .totalPages(response.getTotalPages())
                .build();
    }

    public void markAllAsRead(String email){
        notificationRepo.markAllAsRead(email);
    }

    public void markOneAsRead(Long id){
        notificationRepo.markNotificationAsRead(id);
    }

}