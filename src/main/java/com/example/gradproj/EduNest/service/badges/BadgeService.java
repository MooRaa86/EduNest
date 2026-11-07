package com.example.gradproj.EduNest.service.badges;

import com.example.gradproj.EduNest.dto.badges.request.CreateBadgeRequest;
import com.example.gradproj.EduNest.dto.badges.request.UpdateBadgeRequest;
import com.example.gradproj.EduNest.dto.badges.response.BadgeResponse;
import com.example.gradproj.EduNest.entity.badges.Badge;
import com.example.gradproj.EduNest.entity.badges.BadgeAward;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
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

        // EB-51 — L0 shared gate (mirrors the award and completion gates) so a
        // concurrent badge award or completion notification can never
        // interleave with a badge-point diff; CERT-04 freeze is evaluated
        // while the gate is held.
        MentorShip gate = mentorShipRepository.findForUpdateById(badge.getMentorship().getId())
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));

        Integer oldPoints = badge.getPoints();

        if (req.getTitle() != null) badge.setTitle(req.getTitle());
        if (req.getCategory() != null) badge.setCategory(req.getCategory());
        if (req.getDescription() != null) badge.setDescription(req.getDescription());
        if (req.getPoints() != null) badge.setPoints(req.getPoints());

        // EB-51 / D-09 (CERT-04) — badge-point freeze: point changes on
        // awarded-surface badges are locked once the mentorship is COMPLETED.
        if (gate.getStatus() == Status.COMPLETED
                && req.getPoints() != null && !oldPoints.equals(req.getPoints())) {
            badge.setPoints(oldPoints);
            throw new globalLogicEx("Badge points are locked once the mentorship is completed");
        }

        Badge savedBadge = badgeRepository.save(badge);

        if (req.getPoints() != null && !oldPoints.equals(req.getPoints())) {
            int diff = req.getPoints() - oldPoints;
            totalPointsRepository.applyBadgePointDiff(
                    badgeId, badge.getMentorship().getId(), diff, getCurrentUserEmail());
        }

        return toDto(savedBadge);
    }

    public void deleteBadge(Long badgeId) {
        Badge badge = badgeRepository.findById(badgeId)
                .orElseThrow(() -> new globalLogicEx("Badge not found"));

        validateOwnership(badge.getMentorship());

        // EB-51 — L0 shared gate: delete serialises against awards so the
        // award's badge reference cannot vanish mid-award.
        mentorShipRepository.findForUpdateById(badge.getMentorship().getId())
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));

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
