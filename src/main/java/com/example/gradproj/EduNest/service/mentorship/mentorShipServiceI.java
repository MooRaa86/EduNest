package com.example.gradproj.EduNest.service.mentorship;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipCreateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipUpdateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.mentorShipFDto;
import com.example.gradproj.EduNest.dto.tasks.response.TaskResponse;
import com.example.gradproj.EduNest.entity.mentorship.mentorShipE;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.mentorShipRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import lombok.RequiredArgsConstructor;
import com.example.gradproj.EduNest.service.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class mentorShipServiceI implements mentorShipService {

    private final mentorShipRepository MentorShipRepository;
    private final TaskRepository taskRepository;

    @Override
    public mentorShipFDto createMentorShip(mentorShipCreateDTO dto) {

        mentorShipE mentorShip = mentorShipE.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .category(dto.getCategory())
                .rating(dto.getRating())
                .difficultyLevel(dto.getDifficultyLevel())
                .build();

        mentorShipE savedMentorShip = MentorShipRepository.save(mentorShip);

        return mentorShipFDto.builder()
                .id(savedMentorShip.getId())
                .title(savedMentorShip.getTitle())
                .description(savedMentorShip.getDescription())
                .category(savedMentorShip.getCategory())
                .rating(savedMentorShip.getRating())
                .difficultyLevel(savedMentorShip.getDifficultyLevel())
                .build();
    }

    @Override
    public mentorShipFDto updateMentorShip(Long mentorShipId, mentorShipUpdateDTO dto) {

        mentorShipE mentorShip = MentorShipRepository.findById(mentorShipId)
                .orElseThrow(() -> new globalLogicEx("MentorShip not found"));

        if (dto.getTitle() != null) mentorShip.setTitle(dto.getTitle());
        if (dto.getDescription() != null) mentorShip.setDescription(dto.getDescription());
        if (dto.getCategory() != null) mentorShip.setCategory(dto.getCategory());
        if (dto.getRating() != null) mentorShip.setRating(dto.getRating());
        if (dto.getDifficultyLevel() != null) mentorShip.setDifficultyLevel(dto.getDifficultyLevel());

        mentorShipE saved = MentorShipRepository.save(mentorShip);

        return mentorShipFDto.builder()
                .id(saved.getId())
                .title(saved.getTitle())
                .description(saved.getDescription())
                .category(saved.getCategory())
                .rating(saved.getRating())
                .difficultyLevel(saved.getDifficultyLevel())
                .build();

    }

    @Override
    public void deleteMentorShip(Long mentorShipId) {

        mentorShipE mentorShip = MentorShipRepository.findById(mentorShipId)
                .orElseThrow(() -> new globalLogicEx("MentorShip not found"));

        mentorShip.getStudents().clear();
        MentorShipRepository.save(mentorShip);

        MentorShipRepository.delete(mentorShip);
    }

    @Override
    public PageResponse<mentorShipFDto> getMentorShips(int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<mentorShipE> mentorShipsPage = MentorShipRepository.findAll(pageable);

        List<mentorShipFDto> content = mentorShipsPage.getContent()
                .stream()
                .map(mentorShip -> mentorShipFDto.builder()
                        .id(mentorShip.getId())
                        .title(mentorShip.getTitle())
                        .description(mentorShip.getDescription())
                        .category(mentorShip.getCategory())
                        .rating(mentorShip.getRating())
                        .difficultyLevel(mentorShip.getDifficultyLevel())
                        .build())
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
        mentorShipE mentorShip = MentorShipRepository.findById(mentorShipId).orElseThrow(
                () -> new globalLogicEx("MentorShip not found"));

        return mentorShipFDto.builder()
                .id(mentorShip.getId())
                .title(mentorShip.getTitle())
                .description(mentorShip.getDescription())
                .category(mentorShip.getCategory())
                .rating(mentorShip.getRating())
                .difficultyLevel(mentorShip.getDifficultyLevel())
                .build();
    }

    @Override
    public List<TaskResponse> getMentorShipTasks(Long mentorShipId) {
        if(!MentorShipRepository.existsById(mentorShipId)) {
            throw new globalLogicEx("MentorShip not found");
        }

        List<Task> mentorshipTasks = taskRepository.findByMentorshipId(mentorShipId);
        return mentorshipTasks.stream()
                .map(this::mapToTaskResponse)
                .collect(Collectors.toList());

    }

    @Override
    public long countMentorShipsForMentorId(Long mentorId) {
        if(!MentorShipRepository.existsById(mentorId)) {
            throw new globalLogicEx("Mentor not found");
        }
        return MentorShipRepository.countByMentorId(mentorId);
    }

    @Override
    public long countStudentsforMentorId(Long mentorId) {
        if(!MentorShipRepository.existsById(mentorId)) {
            throw new globalLogicEx("Mentor not found");
        }
        return MentorShipRepository.countByMentorId(mentorId);
    }


    private mentorShipFDto mapToMentorShipResponse(mentorShipE mentorShip) {
        return mentorShipFDto.builder()
                .id(mentorShip.getId())
                .title(mentorShip.getTitle())
                .description(mentorShip.getDescription())
                .category(mentorShip.getCategory())
                .rating(mentorShip.getRating())
                .difficultyLevel(mentorShip.getDifficultyLevel())
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
