package com.example.gradproj.EduNest.service.mentorShip;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipCreateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipUpdateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.mentorShipFDto;
import com.example.gradproj.EduNest.dto.tasks.response.TaskResponse;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Tags;
import com.example.gradproj.EduNest.entity.mentorship.WhatWillLearn;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class mentorShipServiceI implements mentorShipService{

    private final MentorShipRepository MentorShipRepository;
    private final TaskRepository taskRepository;
    private final MentorRepository mentorRepository;
    private final imageStorageService imageService;
    private final EnrollmentRepository enrollmentRepository;


    private String getCurrentMentorEmail() {
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
        Mentor mentor = mentorRepository.findByEmail(getCurrentMentorEmail());

        MentorShip mentorShip = MentorShip.builder()
                .mentor(mentor)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .category(dto.getCategory().trim().toLowerCase())
                .difficultyLevel(dto.getDifficultyLevel())
                .price(dto.getPrice())
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

        Mentor currentMentor = mentorRepository.findByEmail(getCurrentMentorEmail());

        if (!mentorShip.getMentor().getId().equals(currentMentor.getId())) {
            throw new BadCredentialsException("you are not allowed to update this mentorship");
        }

        if (dto.getTitle() != null)
            mentorShip.setTitle(dto.getTitle());

        if (dto.getDescription() != null)
            mentorShip.setDescription(dto.getDescription());

        if (dto.getCategory() != null)
            mentorShip.setCategory(dto.getCategory().trim().toLowerCase());

        if (dto.getDifficultyLevel() != null)
            mentorShip.setDifficultyLevel(dto.getDifficultyLevel());

        if (dto.getPrice() != null)
            mentorShip.setPrice(dto.getPrice());

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
    public void deleteMentorShip(Long mentorShipId) {

        MentorShip mentorShip = MentorShipRepository.findById(mentorShipId)
                .orElseThrow(() -> new globalLogicEx("MentorShip not found"));

        Mentor currentMentor = mentorRepository.findByEmail(getCurrentMentorEmail());

        if (!mentorShip.getMentor().getId().equals(currentMentor.getId())) {
            throw new BadCredentialsException("you are not allowed to delete this mentorship");
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
    @PreAuthorize("hasRole('MENTOR')")
    public void updateMentorShipStatus(long mentorShipId, Status status) {
        MentorShip mentorShip = MentorShipRepository.findById(mentorShipId).orElseThrow(
                () -> new globalLogicEx("MentorShip not found"));

        Mentor currentMentor = mentorRepository.findByEmail(getCurrentMentorEmail());

        if (!mentorShip.getMentor().getId().equals(currentMentor.getId())) {
            throw new BadCredentialsException("you are not allowed to update this mentorship");
        }

        mentorShip.setStatus(status);
        MentorShipRepository.save(mentorShip);
    }

    @Override
    @PreAuthorize("hasRole('MENTOR')")
    public long countMentorShipsForMentorId() {
        Mentor currentMentor = mentorRepository.findByEmail(getCurrentMentorEmail());
        return MentorShipRepository.countByMentor_Id(currentMentor.getId());
    }

    @Override
    @PreAuthorize("hasRole('MENTOR')")
    public long countStudentsforMentor() {
        Mentor currentMentor = mentorRepository.findByEmail(getCurrentMentorEmail());
        return enrollmentRepository.countStudentsByMentorId(currentMentor.getId());
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('MENTOR')")
    public String uploadCoverImage(Long mentorshipId, MultipartFile image) {

        MentorShip mentorship = MentorShipRepository.findById(mentorshipId)
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));

        if (!mentorship.getMentor().getEmail().equals(getCurrentMentorEmail())) {
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


    private mentorShipFDto mapToMentorShipResponse(MentorShip mentorShip) {
        return mentorShipFDto.builder()
                .id(mentorShip.getId())
                .title(mentorShip.getTitle())
                .description(mentorShip.getDescription())
                .difficultyLevel(mentorShip.getDifficultyLevel())
                .price(mentorShip.getPrice())
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
