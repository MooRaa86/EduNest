package com.example.gradproj.EduNest.service.mentorship;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipCreateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.request.mentorShipUpdateDTO;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.mentorShipFDto;
import com.example.gradproj.EduNest.dto.tasks.response.TaskResponse;

import java.util.List;


public interface mentorShipService {

    mentorShipFDto createMentorShip(mentorShipCreateDTO dto);

    mentorShipFDto updateMentorShip(Long mentorShipId, mentorShipUpdateDTO dto);

    void deleteMentorShip(Long mentorShipId);

    PageResponse<mentorShipFDto> getMentorShips(int page, int size);

    mentorShipFDto getMentorShipById(Long mentorShipId);

    List<TaskResponse> getMentorShipTasks(Long mentorShipId);

    long countMentorShipsForMentorId(Long mentorId);

    long countStudentsforMentorId(Long mentorId);

    //    List<mentorShip> getMentorShipsByMentorId(Long mentorId);
//
//
//    mentorShip addStudentToMentorShip(Long mentorShipId, Long studentId);
//
//    mentorShip removeStudentFromMentorShip(Long mentorShipId, Long studentId);
//
//    List<Long> getStudentsIdsInMentorShip(Long mentorShipId);
//
//
//    List<mentorShip> searchMentorShipsByTitle(String title);
//
//    List<mentorShip> filterMentorShipsByCategory(String category);
//
//    List<mentorShip> filterMentorShipsByDifficultyLevel(String difficultyLevel);
//
//
//    mentorShip updateMentorShipRating(Long mentorShipId, Integer rating);
}

