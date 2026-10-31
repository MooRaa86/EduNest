package com.example.gradproj.EduNest.service.studentMentorship;

import com.example.gradproj.EduNest.dto.mentorShipDTOs.response.PageResponse;
import com.example.gradproj.EduNest.dto.studentMentorship.MsLeaderBoardDto;
import com.example.gradproj.EduNest.dto.studentMentorship.LeaderboardEntry;
import com.example.gradproj.EduNest.dto.studentMentorship.StudentInLeaderboardDto;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorshipLeaderboardService {

    private final TotalPointsRepository totalPointsRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final MentorShipRepository mentorShipRepository;

    public MsLeaderBoardDto getMentorshipLeaderboard(Long mentorshipId, int size, int page, String studentEmail) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                || mentorshipId == null || !mentorShipRepository.existsById(mentorshipId)) {
            throw new AccessDeniedException("Leaderboard unavailable");
        }
        String email = auth.getName();
        boolean student = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));
        boolean isEnrolled = student && enrollmentRepository.existsByMentorShip_IdAndStudent_Email(mentorshipId, email);
        boolean owner = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MENTOR"))
                && mentorShipRepository.existsByIdAndMentor_Email(mentorshipId, email);
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isEnrolled && !owner && !admin) throw new AccessDeniedException("Leaderboard unavailable");
        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<StudentInLeaderboardDto> leaderboardPage = totalPointsRepository.findLeaderboardByMentorshipId(mentorshipId, pageable);

        var content = new ArrayList<>(leaderboardPage.getContent());

        for (int i = 0; i < content.size(); i++) {
            long rank = pageable.getOffset() + i + 1;
            content.get(i).setRank(rank);
        }

        StudentInLeaderboardDto currentUser = null;
        long userRank = 0;
        
        if (isEnrolled) {
            currentUser = content.stream()
                    .filter(s -> s.getStudentEmail().equals(email))
                    .findFirst()
                    .orElse(null);
            
            if (currentUser == null) {
                currentUser = totalPointsRepository.findStudentInLeaderboard(mentorshipId, email).orElse(null);
                if (currentUser != null) {
                    content.add(currentUser);
                }
            }
            
            if (currentUser != null) {
                userRank = currentUser.getRank();
            }
        }
        
        PageResponse<LeaderboardEntry> leaderboard = PageResponse.<LeaderboardEntry>builder()
                .content(content.stream().map(this::toEntry).toList())
                .page(leaderboardPage.getNumber())
                .size(leaderboardPage.getSize())
                .totalElements(leaderboardPage.getTotalElements())
                .totalPages(leaderboardPage.getTotalPages())
                .build();
        
        return MsLeaderBoardDto.builder()
                .leaderboard(leaderboard)
                .currentUser(currentUser == null ? null : toEntry(currentUser))
                .totalStudents(leaderboardPage.getTotalElements())
                .userRank(userRank)
                .build();
    }

    /** D-04 amendment (Ruler B): enrollment-gated row = name, email, image URL, rank, points. */
    private LeaderboardEntry toEntry(StudentInLeaderboardDto row) {
        return new LeaderboardEntry(row.getStudentFullName(), row.getStudentEmail(),
                row.getStudentProfileImageUrl(), row.getRank(), row.getTotalPoints());
    }
}
