package com.example.gradproj.EduNest.service.mentorShip;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipCreateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipUpdateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.mentorShipFDto;
import com.example.gradproj.EduNest.dto.tasks.response.TaskResponse;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


public interface mentorShipService {

    mentorShipFDto createMentorShip(mentorShipCreateDTO dto);

    mentorShipFDto updateMentorShip(Long mentorShipId, mentorShipUpdateDTO dto);

    void deleteMentorShip(Long mentorShipId);

    PageResponse<mentorShipFDto> getMentorShips(int page, int size);

    mentorShipFDto getMentorShipById(Long mentorShipId);

    List<TaskResponse> getMentorShipTasks(Long mentorShipId);

    void updateMentorShipStatus(long mentorShipId, Status status);

    long countMentorShipsForMentorId();

    long countStudentsforMentor();

    String uploadCoverImage(Long mentorshipId, MultipartFile image);
}

