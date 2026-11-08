package com.example.gradproj.EduNest.service.badges;

import com.example.gradproj.EduNest.dto.badges.request.CreateBadgeRequest;
import com.example.gradproj.EduNest.dto.badges.request.UpdateBadgeRequest;
import com.example.gradproj.EduNest.dto.badges.response.BadgeResponse;
import com.example.gradproj.EduNest.entity.badges.Badge;
import com.example.gradproj.EduNest.entity.badges.BadgeAward;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.badge.BadgeCategory;
import com.example.gradproj.EduNest.exception.ConcurrentModificationConflict;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.badges.BadgeAwardRepository;
import com.example.gradproj.EduNest.repository.badges.BadgeRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.service.points.TotalPointsServiceImp;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final TotalPointsServiceImp totalPointsService;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;
    private final BadgeLoadedState loadedState;

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
        // JDBC reads share the caller's transaction but do not trigger a JPA auto-flush
        // before the gate. Never refresh the caller's managed object or its graph.
        var owner = jdbc.query("select m.id, u.email from badges b join mentorship m on m.id=b.mentorship_id "
                        + "join users u on u.id=m.mentor_id where b.id=?",
                (rs, row) -> new BadgeOwner(rs.getLong(1), rs.getString(2)), badgeId).stream().findFirst()
                .orElseThrow(() -> new globalLogicEx("Badge not found"));
        if (!owner.email().equals(getCurrentUserEmail())) {
            throw new AccessDeniedException("You are not authorized to manage this mentorship's badges");
        }
        var statuses = jdbc.query("select status from mentorship where id=? for update",
                (rs, row) -> rs.getString(1), owner.mentorshipId());
        if (statuses.isEmpty()) throw new globalLogicEx("Mentorship not found");
        var authoritative = jdbc.query("select title, description, category, points, version, mentorship_id "
                        + "from badges where id=? for update",
                (rs, row) -> new LockedBadge(new BadgeLoadedState.Values(rs.getString(1), rs.getString(2),
                        BadgeCategory.valueOf(rs.getString(3)), rs.getInt(4), rs.getLong(5)), rs.getLong(6)), badgeId)
                .stream().findFirst().orElseThrow(() -> new globalLogicEx("Badge not found"));
        if (authoritative.mentorshipId() != owner.mentorshipId()) {
            throw new ConcurrentModificationConflict("Badge ownership changed concurrently");
        }
        Badge badge = badgeRepository.findById(badgeId)
                .orElseThrow(() -> new globalLogicEx("Badge not found"));
        if (!badge.getMentorship().getId().equals(owner.mentorshipId())) {
            throw new ConcurrentModificationConflict("Badge ownership changed concurrently");
        }
        var original = loadedState.requireOriginal(badge);
        loadedState.requireUnchanged(original, authoritative.values());
        loadedState.rejectOverlappingChange(original.title(), badge.getTitle(), req.getTitle());
        loadedState.rejectOverlappingChange(original.description(), badge.getDescription(), req.getDescription());
        loadedState.rejectOverlappingChange(original.category(), badge.getCategory(), req.getCategory());
        loadedState.rejectOverlappingChange(original.points(), badge.getPoints(), req.getPoints());

        Integer oldPoints = badge.getPoints();

        if (req.getTitle() != null) badge.setTitle(req.getTitle());
        if (req.getCategory() != null) badge.setCategory(req.getCategory());
        if (req.getDescription() != null) badge.setDescription(req.getDescription());
        if (req.getPoints() != null) badge.setPoints(req.getPoints());

        // EB-51 / D-09 (CERT-04) — badge-point freeze: point changes on
        // awarded-surface badges are locked once the mentorship is COMPLETED.
        if (Status.COMPLETED.name().equals(statuses.get(0))
                && req.getPoints() != null && !oldPoints.equals(req.getPoints())) {
            badge.setPoints(oldPoints);
            throw new globalLogicEx("Badge points are locked once the mentorship is completed");
        }

        Badge savedBadge = badgeRepository.save(badge);

        if (req.getPoints() != null && !oldPoints.equals(req.getPoints())) {
            int diff = req.getPoints() - oldPoints;
            List<Student> awardedStudents = badgeAwardRepository.findDistinctStudentsByBadgeId(badgeId);
            for (Student student : awardedStudents) {
                totalPointsService.applyDelta(student, badge.getMentorship(), diff);
            }
        }

        return toDto(savedBadge);
    }

    private record BadgeOwner(long mentorshipId, String email) { }
    private record LockedBadge(BadgeLoadedState.Values values, long mentorshipId) { }

    public void deleteBadge(Long badgeId) {
        Badge badge = badgeRepository.findById(badgeId)
                .orElseThrow(() -> new globalLogicEx("Badge not found"));

        validateOwnership(badge.getMentorship());

        // EB-51 — L0 shared gate: delete serialises against awards so the
        // award's badge reference cannot vanish mid-award.
        mentorShipRepository.findForUpdateById(badge.getMentorship().getId())
                .orElseThrow(() -> new globalLogicEx("Mentorship not found"));

        // EB-51 — acquire locked badge from repository under the L0 gate
        badge = badgeRepository.findForUpdateById(badgeId)
                .orElseThrow(() -> new globalLogicEx("Badge not found"));

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
