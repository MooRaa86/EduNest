package com.example.gradproj.EduNest.dto.studentMentorship;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MsLeaderBoardDto {
    private PageResponse<StudentInLeaderboardDto> leaderboard;
    private StudentInLeaderboardDto currentUser;
    private Long totalStudents;
    private long userRank;
}
