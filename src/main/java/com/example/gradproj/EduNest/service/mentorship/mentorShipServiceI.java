package com.example.gradproj.EduNest.service.mentorship;


import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipCreateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipUpdateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.mentorShipFDto;
import com.example.gradproj.EduNest.entity.mentorship.Mentorship;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorship.mentorShipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class mentorShipServiceI implements mentorShipService {

    private final mentorShipRepository MentorShipRepository;

    @Override
    public mentorShipFDto createMentorShip(mentorShipCreateDTO dto) {

        Mentorship mentorShip = Mentorship.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .category(dto.getCategory())
                .rating(dto.getRating())
                .difficultyLevel(dto.getDifficultyLevel())
                .build();

        Mentorship savedMentorShip = MentorShipRepository.save(mentorShip);

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

        Mentorship mentorShip = MentorShipRepository.findById(mentorShipId)
                .orElseThrow(() -> new globalLogicEx("MentorShip not found"));

        if (dto.getTitle() != null) mentorShip.setTitle(dto.getTitle());
        if (dto.getDescription() != null) mentorShip.setDescription(dto.getDescription());
        if (dto.getCategory() != null) mentorShip.setCategory(dto.getCategory());
        if (dto.getRating() != null) mentorShip.setRating(dto.getRating());
        if (dto.getDifficultyLevel() != null) mentorShip.setDifficultyLevel(dto.getDifficultyLevel());

        Mentorship saved = MentorShipRepository.save(mentorShip);

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

        Mentorship mentorShip = MentorShipRepository.findById(mentorShipId)
                .orElseThrow(() -> new globalLogicEx("MentorShip not found"));

        mentorShip.getStudents().clear();
        MentorShipRepository.save(mentorShip);

        MentorShipRepository.delete(mentorShip);
    }

    @Override
    public PageResponse<mentorShipFDto> getMentorShips(int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<Mentorship> mentorShipsPage = MentorShipRepository.findAll(pageable);

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
        Mentorship mentorShip = MentorShipRepository.findById(mentorShipId).orElseThrow(
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

}