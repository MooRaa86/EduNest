package com.example.gradproj.EduNest.service.mentorShip;

import com.example.gradproj.EduNest.config.integrations.StripeProperties;
import com.example.gradproj.EduNest.utils.PaginationPolicy;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.CreateReviewRequest;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipCreateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipUpdateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.JoinMentorshipOutcome;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.MentorshipExploreDto;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.mentorShipFDto;
import com.example.gradproj.EduNest.dto.tasks.response.TaskResponse;
import com.example.gradproj.EduNest.entity.mentorship.*;
import com.example.gradproj.EduNest.entity.payment.PaymentStatus;
import com.example.gradproj.EduNest.entity.payment.PaymentTransaction;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.notification.NotificationType;
import com.example.gradproj.EduNest.exception.DuplicateResourceException;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import org.springframework.dao.DataIntegrityViolationException;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.mentorShip.ReviewsRepository;
import com.example.gradproj.EduNest.repository.payment.PaymentTransactionRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import com.example.gradproj.EduNest.service.completion.CompletionSnapshotStore;
import com.example.gradproj.EduNest.service.integration.stripe.StripeCheckoutRequest;
import com.example.gradproj.EduNest.service.integration.stripe.StripeCheckoutSession;
import com.example.gradproj.EduNest.service.integration.stripe.StripePaymentGateway;
import com.example.gradproj.EduNest.service.notification.NotificationService;
import com.example.gradproj.EduNest.service.security.ContentReadGuard;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class mentorShipServiceI implements mentorShipService{

    private final MentorShipRepository MentorShipRepository;
    private final TaskRepository taskRepository;
    private final MentorRepository mentorRepository;
    private final ImageStorageService imageService;
    private final CompletionSnapshotStore completionSnapshotStore;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final ReviewsRepository reviewsRepository;
    private final TotalPointsRepository totalPointsRepository;
    private final NotificationService notificationService;
    private final CommissionService commissionService;
    private final ContentReadGuard contentReadGuard;
    // [EB-108][P4-18] Stripe-backed enrollment payments (D-28). The gateway bean
    // is DisabledStripePaymentGateway while STRIPE_ENABLED=false; the disabled
    // branch below keeps the legacy direct-join in that state (C decision).
    private final StripeProperties stripeProperties;
    private final StripePaymentGateway stripePaymentGateway;
    private final PaymentTransactionRepository paymentTransactionRepository;


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
        imageService.deleteOldCoverImageAfterCommit(mentorShip.getCoverImageUrl());
        mentorShip.setCoverImageUrl(null);
        MentorShipRepository.save(mentorShip);
        MentorShipRepository.delete(mentorShip);
    }

    @Override
    public PageResponse<mentorShipFDto> getMentorShips(int page, int size) {

        Pageable pageable = PaginationPolicy.ofClamped(page, size);

        // P1-15 / EB-22 — D-02 default (a): the public overview excludes DRAFT
        // for every caller; owners see their drafts only in the mentor
        // dashboard (findMentorMentorships, mentor-scoped).
        Page<MentorShip> mentorShipsPage = MentorShipRepository.findByStatusNotAndMentor_DeletedFalse(Status.DRAFT, pageable);

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

        if (mentorShip.getMentor() != null && mentorShip.getMentor().isDeleted()
                && !contentReadGuard.isOwnerMentorOrAdmin(mentorShipId)) {
            throw new jakarta.persistence.EntityNotFoundException("MentorShip not found");
        }

        // P1-15 / EB-22 — D-02 default (a): DRAFT detail is confined to its
        // owner-mentor and ADMIN. Everyone else (including anonymous) gets the
        // same 404 shape — no existence or status leak (EntityNotFoundException
        // maps to 404 ERR_RESOURCE_NOT_FOUND in GlobalExceptionHandler).
        if (mentorShip.getStatus() == Status.DRAFT
                && !contentReadGuard.isOwnerMentorOrAdmin(mentorShipId)) {
            throw new jakarta.persistence.EntityNotFoundException("MentorShip not found");
        }

        return mapToMentorShipResponse(mentorShip);
    }

    @Override
    public List<TaskResponse> getMentorShipTasks(Long mentorShipId) {
        contentReadGuard.requireMentorship(mentorShipId);
        boolean studentView = contentReadGuard.isStudentView(mentorShipId);

        List<Task> mentorshipTasks = taskRepository.findByWeek_Mentorship_Id(mentorShipId);
        return mentorshipTasks.stream()
                .filter(task -> !studentView || task.getStatus() == TaskStatus.PUBLISHED || task.getStatus() == TaskStatus.CLOSED)
                .map(this::mapToTaskResponse)
                .collect(Collectors.toList());

    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('MENTOR')")
    public void updateMentorShipStatus(long mentorShipId, Status status) {
        // EB-51 — L0 shared gate acquired at method start: the COMPLETED
        // transition and its certificate fan-out are serialised against
        // badge point edits and new badge awards so no award (ledger) can
        // interleave between the final certificate truth and the freeze.
        MentorShip mentorShip = MentorShipRepository.findForUpdateById(mentorShipId).orElseThrow(
                () -> new globalLogicEx("MentorShip not found"));

        Mentor currentMentor = mentorRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Mentor not found"));

        if (!mentorShip.getMentor().getId().equals(currentMentor.getId())) {
            throw new BadCredentialsException("you are not allowed to update this mentorship");
        }

        if (status == mentorShip.getStatus()) {
            if (status == Status.COMPLETED) return;
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
            String mentorName = currentMentor.getFirstName() + " " + currentMentor.getLastName();
            completionSnapshotStore.create(mentorShipId, mentorShip.getTitle(), getCurrentUserEmail(), mentorName);

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

        if (image.getSize() > 5 * 1024 * 1024) {
            throw new globalLogicEx("Image size must be less than 5MB");
        }

        String oldImageUrl = mentorship.getCoverImageUrl();

        String imageUrl =
                imageService.saveCoverImage(mentorshipId, image);

        imageService.deleteOldCoverImageAfterCommit(oldImageUrl);

        mentorship.setCoverImageUrl(imageUrl);
        MentorShipRepository.save(mentorship);

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

        imageService.deleteOldCoverImageAfterCommit(mentorship.getCoverImageUrl());
        mentorship.setCoverImageUrl(null);
    }

    @Override
    @Transactional
    public JoinMentorshipOutcome joinMentorship(Long mentorshipId) {

        String email = getCurrentUserEmail();

        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));

        MentorShip mentorShip = MentorShipRepository.findForUpdateById(mentorshipId)
                .orElseThrow(() -> new UsernameNotFoundException("Mentorship not found"));


        if(mentorShip.getStatus() == Status.DRAFT || mentorShip.getStatus() == Status.COMPLETED) {
            throw new globalLogicEx("This mentorship is not available to join");
        }

        if (enrollmentRepository.existsByMentorShip_IdAndStudent_Id(
                mentorshipId,
                student.getId()
        )) {
            // [EB-44][P3-02] MSH-04 uniqueness -> 409 ERR_DUPLICATE_RESOURCE
            throw new DuplicateResourceException("You are already enrolled in this mentorship");
        }

        // [EB-108][P4-18] D-28: the amount is computed SERVER-side from the
        // mentorship records only — never from client input.
        Double priceAfterDiscount = mentorShip.getPrice() - (mentorShip.getPrice() * mentorShip.getDiscountPercentage()/100);
        long amountCents = Math.round(priceAfterDiscount * 100);

        // [EB-108] Free Tier Bypass (Omar, 2026-11-12): a mentorship whose
        // effective price is $0 joins instantly through the legacy path —
        // Stripe is NEVER invoked for a zero-amount join. STRIPE_ENABLED=false
        // keeps the legacy direct-join for every price too (C decision), so
        // local dev and the full test suite run exactly as before.
        if (amountCents <= 0 || !stripeProperties.isEnabled()) {
            finalizeEnrollment(student, mentorShip, priceAfterDiscount, true);
            return JoinMentorshipOutcome.directEnrollment();
        }

        // Money path (Stripe enabled + amount > 0). One unpaid checkout per
        // (student, mentorship): no stacking of pending sessions; replays of
        // completed sessions are handled idempotently by the webhook instead.
        if (paymentTransactionRepository.existsByStudent_IdAndMentorShip_IdAndStatus(
                student.getId(), mentorshipId, PaymentStatus.PENDING)) {
            throw new DuplicateResourceException(
                    "You already have a pending payment for this mentorship - complete it before starting a new one");
        }

        // The transaction's public id travels in the Checkout metadata so the
        // webhook can resolve the attempt; it is generated up front because
        // Stripe sees it before we persist anything.
        UUID transactionPublicId = UUID.randomUUID();
        StripeCheckoutSession session = stripePaymentGateway.createCheckoutSession(
                new StripeCheckoutRequest(
                        transactionPublicId.toString(),
                        student.getPublicId().toString(),
                        student.getEmail(),
                        mentorShip.getPublicId().toString(),
                        mentorShip.getTitle(),
                        amountCents));

        // If the Stripe call above succeeded but this save fails, the session
        // simply expires unpaid — no enrollment, no money path side effects.
        PaymentTransaction transaction = PaymentTransaction.builder()
                .publicId(transactionPublicId)
                .student(student)
                .mentorShip(mentorShip)
                .amount(priceAfterDiscount)
                .currency(stripeProperties.getCurrency())
                .status(PaymentStatus.PENDING)
                .stripeSessionId(session.sessionId())
                .build();
        paymentTransactionRepository.save(transaction);

        // The enrollment does NOT exist at this point — only the signature-
        // verified webhook flipping this transaction to PAID creates it.
        return JoinMentorshipOutcome.checkoutRequired(session.checkoutUrl());
    }

    /**
     * [EB-108][P4-18] webhook-side finalization for a PAID transaction (the
     * sole source of truth for enrollment creation — D-28). Uses the PAID
     * amount snapshot, even if the mentor has since changed the price: the
     * student pays/has paid what the checkout session stated.
     */
    @Override
    @Transactional
    public void finalizePaidEnrollment(PaymentTransaction transaction) {
        finalizeEnrollment(transaction.getStudent(), transaction.getMentorShip(), transaction.getAmount(), false);
    }

    /**
     * The legacy enrollment finalization block, shared verbatim by both paths:
     * the direct join (free bypass / Stripe disabled, throwOnDuplicate=true to
     * preserve the 409 MSH-04 semantics) and the webhook PAID path
     * (throwOnDuplicate=false — a replay or double-payment on an already-
     * enrolled pair is a no-op, never an error loop for Stripe retries).
     */
    private void finalizeEnrollment(Student student, MentorShip mentorShip, Double priceAfterDiscount,
                                    boolean throwOnDuplicate) {

        if (enrollmentRepository.existsByMentorShip_IdAndStudent_Id(
                mentorShip.getId(), student.getId())) {
            if (throwOnDuplicate) {
                throw new DuplicateResourceException("You are already enrolled in this mentorship");
            }
            return;
        }

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

        try {
            // [EB-44][P3-02] racing join on the same (student, mentorship) pair
            // hits uk_enrollment_student_mentorship -> translate to 409
            enrollmentRepository.save(enrollment);
        } catch (DataIntegrityViolationException dive) {
            if (throwOnDuplicate) {
                throw new DuplicateResourceException("You are already enrolled in this mentorship");
            }
            // Racing webhook: the other attempt won the unique pair; the PAID
            // transaction record stands but no duplicate enrollment is created.
        }
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

            try {
                // [EB-44][P3-02] rating race on (student, mentorship) resolves via
                // uk_mentorship_review_student -> translate to 409
                reviewsRepository.save(review);
                reviewsRepository.flush();
            } catch (DataIntegrityViolationException dive) {
                throw new DuplicateResourceException("You have already reviewed this mentorship.");
            }
        }

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
        Pageable pageable = PaginationPolicy.ofClamped(page, size);
        String cleanKeyword = sanitizeKeyword(keyword);
        String cleanCategory = (category != null && !category.isBlank()) ? category.trim() : null;
        Page<MentorshipExploreDto> result = MentorShipRepository.searchMentorShips(cleanKeyword, cleanCategory, minPrice, maxPrice, pageable);
        return PageResponse.<MentorshipExploreDto>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    /**
     * MSH-12: Sanitize explore search keyword.
     * Trims, lowercases, and escapes SQL LIKE wildcard characters (\, %, _)
     * so user input is matched literally rather than acting as a SQL regex.
     */
    private String sanitizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
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
                .id(mentorShip.getPublicId())
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
        res.setId(task.getPublicId());
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
    private Long resolveMentorshipId(UUID publicId) {
        return MentorShipRepository.findIdByPublicId(publicId)
                .orElseThrow(() -> new globalLogicEx("Mentorship not found with UUID: " + publicId));
    }

    @Override
    @Transactional
    public mentorShipFDto updateMentorShip(UUID mentorShipId, mentorShipUpdateDTO dto) {
        return updateMentorShip(resolveMentorshipId(mentorShipId), dto);
    }

    @Override
    @Transactional
    public void deleteMentorShip(UUID mentorShipId) {
        deleteMentorShip(resolveMentorshipId(mentorShipId));
    }

    @Override
    public mentorShipFDto getMentorShipById(UUID mentorShipId) {
        return getMentorShipById(resolveMentorshipId(mentorShipId));
    }

    @Override
    public List<TaskResponse> getMentorShipTasks(UUID mentorShipId) {
        Long internalId = MentorShipRepository.findIdByPublicId(mentorShipId).orElse(-1L);
        return getMentorShipTasks(internalId);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('MENTOR')")
    public void updateMentorShipStatus(UUID mentorShipId, Status status) {
        updateMentorShipStatus(resolveMentorshipId(mentorShipId), status);
    }

    @Override
    @Transactional
    public String uploadCoverImage(UUID mentorshipId, MultipartFile image) {
        return uploadCoverImage(resolveMentorshipId(mentorshipId), image);
    }

    @Override
    @Transactional
    public void deleteCoverImage(UUID mentorshipId) {
        deleteCoverImage(resolveMentorshipId(mentorshipId));
    }

    @Override
    @Transactional
    public JoinMentorshipOutcome joinMentorship(UUID mentorshipId) {
        return joinMentorship(resolveMentorshipId(mentorshipId));
    }

    @Override
    @Transactional
    public void rateMentorship(UUID mentorshipId, CreateReviewRequest request) {
        rateMentorship(resolveMentorshipId(mentorshipId), request);
    }
}
