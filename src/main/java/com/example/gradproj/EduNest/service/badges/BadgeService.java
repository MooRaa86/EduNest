package com.example.gradproj.EduNest.service.badges;

import com.example.gradproj.EduNest.dto.badges.request.CreateBadgeRequest;
import com.example.gradproj.EduNest.dto.badges.request.UpdateBadgeRequest;
import com.example.gradproj.EduNest.dto.badges.response.BadgeResponse;
import com.example.gradproj.EduNest.entity.badges.Badge;
import com.example.gradproj.EduNest.entity.badges.BadgeAward;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.badges.BadgeAwardRepository;
import com.example.gradproj.EduNest.repository.badges.BadgeRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class BadgeService {

    private final BadgeRepository badgeRepository;
    private final BadgeAwardRepository badgeAwardRepository;
    private final MentorShipRepository mentorShipRepository;
    private final MentorRepository mentorRepository;
    private final TotalPointsRepository totalPointsRepository;

    private String getCurrentUserEmail() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .orElseThrow(() -> new AccessDeniedException("Unauthenticated user"));
    }

    private Long getCurrentMentorId() {
        return mentorRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new AccessDeniedException("Mentor not found"))
                .getId();
    }

    private void validateOwnership(MentorShip mentorship) {
        if (!mentorship.getMentor().getId().equals(getCurrentMentorId()))
            throw new AccessDeniedException("You are not authorized to manage this mentorship's badges");
    }

    public BadgeResponse createBadge(Long mentorshipId, CreateBadgeRequest req) {
        MentorShip mentorship = mentorShipRepository.findById(mentorshipId)
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));

        validateOwnership(mentorship);

        if (badgeRepository.countByMentorship_Id(mentorshipId) >= 10)
            throw new globalLogicEx("Mentorship already has the maximum of 10 badges");

        Badge badge = Badge.builder()
                .mentorship(mentorship)
                .title(req.getTitle())
                .category(req.getCategory())
                .description(req.getDescription())
                .points(req.getPoints())
                .build();

        return toDto(badgeRepository.save(badge));
    }

    @Transactional(readOnly = true)
    public BadgeResponse getBadgeById(Long badgeId) {
        return toDto(badgeRepository.findById(badgeId)
                .orElseThrow(() -> new globalLogicEx("Badge not found")));
    }

    @Transactional(readOnly = true)
    public List<BadgeResponse> getBadgesByMentorship(Long mentorshipId) {
        return badgeRepository.findByMentorship_Id(mentorshipId)
                .stream().map(this::toDto).toList();
    }

    public BadgeResponse updateBadge(Long badgeId, UpdateBadgeRequest req) {
        Badge badge = badgeRepository.findById(badgeId)
                .orElseThrow(() -> new globalLogicEx("Badge not found"));

        validateOwnership(badge.getMentorship());

        Integer oldPoints = badge.getPoints();

        if (req.getTitle() != null) badge.setTitle(req.getTitle());
        if (req.getCategory() != null) badge.setCategory(req.getCategory());
        if (req.getDescription() != null) badge.setDescription(req.getDescription());
        if (req.getPoints() != null) badge.setPoints(req.getPoints());

        Badge savedBadge = badgeRepository.save(badge);

        if (req.getPoints() != null && !oldPoints.equals(req.getPoints())) {
            int diff = req.getPoints() - oldPoints;
            totalPointsRepository.updatePointsForBadgeAwards(badgeId, badge.getMentorship().getId(), diff);
        }

        return toDto(savedBadge);
    }

    public void deleteBadge(Long badgeId) {
        Badge badge = badgeRepository.findById(badgeId)
                .orElseThrow(() -> new globalLogicEx("Badge not found"));

        validateOwnership(badge.getMentorship());

        if (badgeAwardRepository.existsByBadge_Id(badgeId))
            throw new globalLogicEx("Badge has already been awarded and cannot be deleted");
        badgeRepository.deleteById(badgeId);
    }

    private BadgeResponse toDto(Badge b) {
        return BadgeResponse.builder()
                .id(b.getId())
                .mentorshipId(b.getMentorship().getId())
                .title(b.getTitle())
                .category(b.getCategory())
                .description(b.getDescription())
                .points(b.getPoints())
                .build();
    }
}
