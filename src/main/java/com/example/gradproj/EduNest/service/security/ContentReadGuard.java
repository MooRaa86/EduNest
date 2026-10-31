package com.example.gradproj.EduNest.service.security;

import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** D-03: authorize using the authenticated principal, never a caller-supplied email. */
@Component
@RequiredArgsConstructor
public class ContentReadGuard {
    private final MentorShipRepository mentorships;
    private final EnrollmentRepository enrollments;

    private Authentication authentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Authentication required");
        }
        return auth;
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    public String currentEmail() {
        return authentication().getName();
    }

    public boolean isStudentView(Long mentorshipId) {
        Authentication auth = authentication();
        return !hasRole(auth, "ADMIN") && !(hasRole(auth, "MENTOR")
                && mentorships.existsByIdAndMentor_Email(mentorshipId, auth.getName()));
    }

    public void requireMentorship(Long id) {
        Authentication auth = authentication();
        if (id == null || !mentorships.existsById(id)) throw new AccessDeniedException("Content unavailable");
        if (hasRole(auth, "ADMIN")) return;
        if (hasRole(auth, "MENTOR") && mentorships.existsByIdAndMentor_Email(id, auth.getName())) return;
        if (hasRole(auth, "STUDENT") && enrollments.existsByMentorShip_IdAndStudent_Email(id, auth.getName())) return;
        throw new AccessDeniedException("Content unavailable");
    }

    public void requireVisibleToStudent(Long mentorshipId, boolean visible) {
        if (!visible && isStudentView(mentorshipId)) throw new AccessDeniedException("Content unavailable");
    }
}
