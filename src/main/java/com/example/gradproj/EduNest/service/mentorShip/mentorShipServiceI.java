package com.example.gradproj.EduNest.service.mentorShip;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.CreateReviewRequest;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipCreateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipUpdateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.MentorshipExploreDto;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.mentorShipFDto;
import com.example.gradproj.EduNest.dto.tasks.response.TaskResponse;
import com.example.gradproj.EduNest.entity.mentorship.*;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.mentorShip.ReviewsRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.certificate.CertificateService;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class mentorShipServiceI implements mentorShipService{

    private final MentorShipRepository MentorShipRepository;
    private final TaskRepository taskRepository;
    private final MentorRepository mentorRepository;
    private final ImageStorageService imageService;
    private final CertificateService certificateService;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final ReviewsRepository reviewsRepository;
    private final TotalPointsRepository totalPointsRepository;
    private final NotificationService notificationService;
    private final CommissionService commissionService;


    private String getCurrentUserEmail() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated user");
        }
        return authentication.getName();
    }


    @Transactional
    @PreAuthorize("hasRole('MENTOR')")
    @Override
    public mentorShipFDto createMentorShip(mentorShipCreateDTO dto) {
        Mentor mentor = mentorRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Mentor not found"));


        MentorShip mentorShip = MentorShip.builder()
                .mentor(mentor)
                .title(dto.getTitle())
                .subtitle(dto.getSubtitle())
                .description(dto.getDescription())
                .category(dto.getCategory().trim().toLowerCase())
                .difficultyLevel(dto.getDifficultyLevel())
                .price(dto.getPrice())
                .discountPercentage(dto.getDiscountPercentage() != null ? dto.getDiscountPercentage() : 0)
                .duration(dto.getDuration())
                .build();

        List<Tags> tags = dto.getTags().stream()
                .map(t -> Tags.builder()
                        .tag(t.trim().toLowerCase())
                        .mentorShip(mentorShip)
                        .build()).collect(Collectors.toList());

        List<WhatWillLearn> whatWillLearns = dto.getWhatWillLearn().stream()
                        .map(w -> WhatWillLearn.builder()
                                .mentorShip(mentorShip)
                                .content(w.trim().toLowerCase())
                                .build()).collect(Collectors.toList());

        mentorShip.setWhatWillLearn(whatWillLearns);

        mentorShip.setTags(tags);

        MentorShip savedMentorShip = MentorShipRepository.save(mentorShip);

        return mapToMentorShipResponse(savedMentorShip);
    }

    @Transactional
    @PreAuthorize("hasRole('MENTOR')")
    @Override
    public mentorShipFDto updateMentorShip(Long mentorShipId, mentorShipUpdateDTO dto) {

        MentorShip mentorShip = MentorShipRepository.findById(mentorShipId)
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));

        Mentor currentMentor = mentorRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Mentor not found"));

        if (!mentorShip.getMentor().getId().equals(currentMentor.getId())) {
            throw new BadCredentialsException("you are not allowed to update this mentorship");
        }

        if (dto.getTitle() != null)
            mentorShip.setTitle(dto.getTitle());

        if (dto.getSubtitle() != null)
            mentorShip.setSubtitle(dto.getSubtitle());

        if (dto.getDescription() != null)
            mentorShip.setDescription(dto.getDescription());

        if (dto.getCategory() != null)
            mentorShip.setCategory(dto.getCategory().trim().toLowerCase());

        if (dto.getDifficultyLevel() != null)
            mentorShip.setDifficultyLevel(dto.getDifficultyLevel());

        if (dto.getPrice() != null)
            mentorShip.setPrice(dto.getPrice());

        if (dto.getDiscountPercentage() != null)
            mentorShip.setDiscountPercentage(dto.getDiscountPercentage());

        if (dto.getWhatWillLearn() != null) {

            mentorShip.getWhatWillLearn().clear();

            for (String w : dto.getWhatWillLearn()) {
                mentorShip.getWhatWillLearn().add(
                        WhatWillLearn.builder()
                                .mentorShip(mentorShip)
                                .content(w.trim().toLowerCase())
                                .build()
                );
            }
        }

        if (dto.getTags() != null) {
            mentorShip.getTags().clear();
            dto.getTags().forEach(t -> {
                mentorShip.getTags().add(
                        Tags.builder()
                                .tag(t.trim().toLowerCase())
                                .mentorShip(mentorShip)
                                .build()
                );
            });
        }

        if(dto.getDuration() != null){
            mentorShip.setDuration(dto.getDuration());
        }

        return mapToMentorShipResponse(mentorShip);
    }

    @Override
    @PreAuthorize("hasRole('MENTOR')")
    @Transactional
    public void deleteMentorShip(Long mentorShipId) {

        MentorShip mentorShip = MentorShipRepository.findById(mentorShipId)
                .orElseThrow(() -> new globalLogicEx("MentorShip not found"));

        Mentor currentMentor = mentorRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Mentor not found"));


        if (!mentorShip.getMentor().getId().equals(currentMentor.getId())) {
            throw new BadCredentialsException("you are not allowed to delete this mentorship");
        }

        if (mentorShip.getStatus() != Status.DRAFT){
            throw new globalLogicEx("you can't delete this mentorship, " +
                    "because it's not in draft status and it's published");
        }

        mentorShip.getEnrollments().clear();
        imageService.deleteOldCoverImage(mentorShip.getCoverImageUrl());
        mentorShip.setCoverImageUrl(null);
        MentorShipRepository.save(mentorShip);
        MentorShipRepository.delete(mentorShip);
    }

    @Override
    public PageResponse<mentorShipFDto> getMentorShips(int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<MentorShip> mentorShipsPage = MentorShipRepository.findAll(pageable);

        List<mentorShipFDto> content = mentorShipsPage.getContent()
                .stream()
                .map(this::mapToMentorShipResponse)
                .toList();

        return PageResponse.<mentorShipFDto>builder()
                .content(content)
                .page(mentorShipsPage.getNumber())
                .size(mentorShipsPage.getSize())
                .totalElements(mentorShipsPage.getTotalElements())
                .totalPages(mentorShipsPage.getTotalPages())
                .build();
    }

    @Override
    public mentorShipFDto getMentorShipById(Long mentorShipId) {
        MentorShip mentorShip = MentorShipRepository.findById(mentorShipId).orElseThrow(
                () -> new globalLogicEx("MentorShip not found"));

        return mapToMentorShipResponse(mentorShip);
    }

    @Override
    public List<TaskResponse> getMentorShipTasks(Long mentorShipId) {
        if(!MentorShipRepository.existsById(mentorShipId)) {
            throw new globalLogicEx("MentorShip not found");
        }

        List<Task> mentorshipTasks = taskRepository.findByWeek_Mentorship_Id(mentorShipId);
        return mentorshipTasks.stream()
                .map(this::mapToTaskResponse)
                .collect(Collectors.toList());

    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('MENTOR')")
    public void updateMentorShipStatus(long mentorShipId, Status status) {
        MentorShip mentorShip = MentorShipRepository.findById(mentorShipId).orElseThrow(
                () -> new globalLogicEx("MentorShip not found"));

        Mentor currentMentor = mentorRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Mentor not found"));

        if (!mentorShip.getMentor().getId().equals(currentMentor.getId())) {
            throw new BadCredentialsException("you are not allowed to update this mentorship");
        }

        if (status == mentorShip.getStatus()) {
            throw new globalLogicEx("Mentorship is already in this status");
        }

        if(status == Status.DRAFT && mentorShip.getStatus() == Status.ACTIVE) {
            throw new globalLogicEx("you can't change status to draft after be published");
        }

        if(status == Status.COMPLETED && mentorShip.getStatus() == Status.DRAFT) {
            throw new globalLogicEx("you can't change status to completed before being published");
        }

        if(mentorShip.getStatus() == Status.COMPLETED){
            throw new globalLogicEx("you can't change status of completed mentorship");
        }

        if (status == Status.COMPLETED) {
            long enrolledCount = enrollmentRepository.countStudentsByMentorship(mentorShipId);
            if (enrolledCount == 0) {
                throw new globalLogicEx("Cannot complete mentorship with no enrolled students");
            }
        }

        mentorShip.setStatus(status);
        MentorShipRepository.save(mentorShip);

        if(status == Status.COMPLETED){
            certificateService.issueCertificates(mentorShipId);

            notificationService.sendToMentorshipStudents(mentorShipId,
                    "Mentorship Completed",
                    "Your mentorship " + mentorShip.getTitle() + " has been completed, " +
                    "You can now download your certificate, " + "Best regards, " + "EduNest Team",
                    NotificationType.MENTORSHIP
            );

            String mentorName = currentMentor.getFirstName() + " " + currentMentor.getLastName();

            notificationService.sendToAdmin("Mentorship Completed",
                    "Mentorship " + mentorShip.getTitle() + " has been completed, " +
                    "Mentor : " + mentorName,
                    NotificationType.MENTORSHIP
            );

        }

        if (status == Status.ACTIVE){
            notificationService.sendToAdmin("Mentorship Published",
                    "Mentorship " + mentorShip.getTitle() + " has been published, " +
                    "Mentor : " + currentMentor.getFirstName() + " " + currentMentor.getLastName(),
                    NotificationType.MENTORSHIP
            );
        }

    }

    @Override
    @PreAuthorize("hasRole('MENTOR')")
    public long countMentorShipsForMentorId() {
        Mentor currentMentor = mentorRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Mentor not found"));
        return MentorShipRepository.countByMentor_Id(currentMentor.getId());
    }

    @Override
    @PreAuthorize("hasRole('MENTOR')")
    public long countStudentsforMentor() {
        Mentor currentMentor = mentorRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Mentor not found"));
        return enrollmentRepository.countStudentsByMentorId(currentMentor.getId());
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('MENTOR')")
    public String uploadCoverImage(Long mentorshipId, MultipartFile image) {

        MentorShip mentorship = MentorShipRepository.findById(mentorshipId)
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));

        if (!mentorship.getMentor().getEmail().equals(getCurrentUserEmail())) {
            throw new globalLogicEx("You are not allowed for this request");
        }

        if (image.isEmpty()) {
            throw new globalLogicEx("Image is empty");
        }

        if (!image.getContentType().startsWith("image/")) {
            throw new globalLogicEx("File must be an image");
        }

        if (image.getSize() > 2 * 1024 * 1024) {
            throw new globalLogicEx("Image size must be less than 2MB");
        }

        imageService.deleteOldCoverImage(mentorship.getCoverImageUrl());

        String imageUrl =
                imageService.saveCoverImage(mentorshipId, image);

        mentorship.setCoverImageUrl(imageUrl);

        return imageUrl;
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('MENTOR')")
    public void deleteCoverImage(Long mentorshipId) {

        MentorShip mentorship = MentorShipRepository.findById(mentorshipId)
                .orElseThrow(() -> new UsernameNotFoundException("Mentorship not found"));

        if (!mentorship.getMentor().getEmail().equals(getCurrentUserEmail())) {
            throw new globalLogicEx("You are not allowed for this request");
        }

        imageService.deleteOldCoverImage(mentorship.getCoverImageUrl());
        mentorship.setCoverImageUrl(null);
    }

    @Override
    @Transactional
    public void joinMentorship(Long mentorshipId) {

        String email = getCurrentUserEmail();

        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));

        MentorShip mentorShip = MentorShipRepository.findById(mentorshipId)
                .orElseThrow(() -> new UsernameNotFoundException("Mentorship not found"));

        //ToDo complete
        if(mentorShip.getStatus() == Status.DRAFT || mentorShip.getStatus() == Status.COMPLETED) {
            throw new globalLogicEx("This mentorship is not available to join");
        }

        if (enrollmentRepository.existsByMentorShip_IdAndStudent_Id(
                mentorshipId,
                student.getId()
        )) {
            throw new globalLogicEx("You are already enrolled in this mentorship");
        }

        Double priceAfterDiscount = mentorShip.getPrice() - (mentorShip.getPrice() * mentorShip.getDiscountPercentage()/100);
        Double platformProfit = commissionService.calculatePlatformCut(priceAfterDiscount);

        Enrollment enrollment = Enrollment.builder()
                .student(student)
                .mentorShip(mentorShip)
                .price(priceAfterDiscount)
                .platformProfit(platformProfit)
                .joinedAt(LocalDateTime.now())
                .build();

        TotalPoints tp = TotalPoints.builder()
                .student(student)
                .mentorship(mentorShip)
                .totalPoints(0)
                .build();

        String mentorEmail = mentorShip.getMentor().getEmail();
        double mentorEarning = priceAfterDiscount - platformProfit;

        notificationService.sendToUserByEmail(mentorEmail,
                "New Student Enrolled",
                "A new student has enrolled in your mentorship: " + mentorShip.getTitle() + ", "
                        + "Student name: " + student.getFirstName() + " " + student.getLastName() + ", "
                        + "Student email: " + student.getEmail() +
                ", you earn " + mentorEarning + "$",
                NotificationType.MENTORSHIP);

        enrollmentRepository.save(enrollment);
        totalPointsRepository.save(tp);
    }

    @Transactional
    @Override
    public void rateMentorship(Long mentorshipId, CreateReviewRequest request) {

        String email = getCurrentUserEmail();

        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));

        MentorShip mentorShip = MentorShipRepository.findById(mentorshipId)
                .orElseThrow(() -> new UsernameNotFoundException("Mentorship not found"));

        boolean enrolled = enrollmentRepository
                .existsByMentorShip_IdAndStudent_Id(mentorshipId, student.getId());

        if (!enrolled) {
            throw new globalLogicEx("You must enroll before rating");
        }

        MentorShipReviews existingReview =
                reviewsRepository.findByMentorShipIdAndStudentId(
                        mentorshipId,
                        student.getId()
                );

        if (existingReview != null) {

            existingReview.setRating(request.getRating());
            existingReview.setFeedBack(request.getFeedback());

        } else {

            MentorShipReviews review = MentorShipReviews.builder()
                    .mentorShip(mentorShip)
                    .student(student)
                    .rating(request.getRating())
                    .feedBack(request.getFeedback())
                    .build();

            reviewsRepository.save(review);
        }

        reviewsRepository.flush();

        Double avgRating = reviewsRepository.calculateAverageRating(mentorshipId);

        mentorShip.setRating(avgRating != null ? avgRating : 0.0);

        String mentorEmail = mentorShip.getMentor().getEmail();

        notificationService.sendToUserByEmail(mentorEmail,
                "New Rate",
                "you have a new review from student " + student.getFirstName() + " " + student.getLastName() +
                        " in your mentorship " + mentorShip.getTitle() +
                ", feedback: " + request.getFeedback() + ", " + "Rating: " + request.getRating() + ", "
                + "your mentorship now has " + avgRating + " rating"
                ,NotificationType.REVIEW
                );
    }

    @Override
    public PageResponse<MentorshipExploreDto> getMentorShipsExplorePage(String keyword, String category, Double minPrice, Double maxPrice, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MentorshipExploreDto> result = MentorShipRepository.searchMentorShips(keyword, category, minPrice, maxPrice, pageable);
        return PageResponse.<MentorshipExploreDto>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Override
    public List<String> getCategories() {
        return MentorShipRepository.findAllCategories();
    }


    private mentorShipFDto mapToMentorShipResponse(MentorShip mentorShip) {
        double price = mentorShip.getPrice();
        int discount = mentorShip.getDiscountPercentage() != null ? mentorShip.getDiscountPercentage() : 0;
        double priceAfterDiscount = discount > 0 ? price - (price * discount / 100.0) : price;

        return mentorShipFDto.builder()
                .id(mentorShip.getId())
                .title(mentorShip.getTitle())
                .subtitle(mentorShip.getSubtitle())
                .description(mentorShip.getDescription())
                .difficultyLevel(mentorShip.getDifficultyLevel())
                .price(price)
                .discountPercentage(discount)
                .priceAfterDiscount(priceAfterDiscount)
                .whatWillLearn(mentorShip.getWhatWillLearn() == null ? List.of() :
                        mentorShip.getWhatWillLearn().stream()
                                .map(WhatWillLearn::getContent)
                                .toList()
                )
                .tags(
                        mentorShip.getTags() == null ? List.of() :
                                mentorShip.getTags().stream()
                                        .map(Tags::getTag)
                                        .toList()
                )
                .status(mentorShip.getStatus())
                .category(mentorShip.getCategory())
                .rating(
                        mentorShip.getRating() == null ? 0 : mentorShip.getRating()
                )
                .duration(mentorShip.getDuration())
                .coverImageUrl(mentorShip.getCoverImageUrl())
                .build();
    }

    private TaskResponse mapToTaskResponse(Task task) {
        TaskResponse res = new TaskResponse();
        res.setId(task.getId());
        res.setTitle(task.getTitle());
        res.setDescription(task.getDescription());
        res.setPoints(task.getPoints());
        res.setPassPoints(task.getPassPoints());
        res.setEstimatedMinutes(task.getEstimatedMinutes());
        res.setStatus(task.getStatus().name());
        res.setDueAt(task.getDueAt());
        res.setAttachmentUrl(task.getAttachmentUrl());

        return res;
    }

}
