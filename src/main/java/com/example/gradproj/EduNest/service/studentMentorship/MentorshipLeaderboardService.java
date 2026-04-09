package com.example.gradproj.EduNest.service.studentMentorship;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.studentMentorship.MsLeaderBoardDto;
import com.example.gradproj.EduNest.dto.studentMentorship.StudentInLeaderboardDto;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorshipLeaderboardService {

    private final TotalPointsRepository totalPointsRepository;
    private final EnrollmentRepository enrollmentRepository;

    public MsLeaderBoardDto getMentorshipLeaderboard(Long mentorshipId, int size, int page, String studentEmail) {

        boolean isEnrolled = enrollmentRepository.existsByMentorShip_IdAndStudent_Email(mentorshipId, studentEmail);

//        if (!isEnrolled) {
//            throw new globalLogicEx("you can't view the leaderboard");
//        }
        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<StudentInLeaderboardDto> leaderboardPage = totalPointsRepository.findLeaderboardByMentorshipId(mentorshipId, pageable);

        var content = leaderboardPage.getContent();

        for (int i = 0; i < content.size(); i++) {
            long rank = pageable.getOffset() + i + 1;
            content.get(i).setRank(rank);
        }

        StudentInLeaderboardDto currentUser = null;
        long userRank = 0;
        
        if (studentEmail != null && isEnrolled) {
            currentUser = content.stream()
                    .filter(s -> s.getStudentEmail().equals(studentEmail))
                    .findFirst()
                    .orElse(null);
            
            if (currentUser == null) {
                currentUser = totalPointsRepository.findStudentInLeaderboard(mentorshipId, studentEmail).orElse(null);
                if (currentUser != null) {
                    content.add(currentUser);
                }
            }
            
            if (currentUser != null) {
                userRank = currentUser.getRank();
            }
        }
        
        PageResponse<StudentInLeaderboardDto> leaderboard = PageResponse.<StudentInLeaderboardDto>builder()
                .content(content)
                .page(leaderboardPage.getNumber())
                .size(leaderboardPage.getSize())
                .totalElements(leaderboardPage.getTotalElements())
                .totalPages(leaderboardPage.getTotalPages())
                .build();
        
        return MsLeaderBoardDto.builder()
                .leaderboard(leaderboard)
                .currentUser(currentUser)
                .totalStudents(leaderboardPage.getTotalElements())
                .userRank(userRank)
                .build();
    }
}
