package com.example.gradproj.EduNest.service.points;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.AfterEach;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TotalPointsApplyDeltaTest {

    private TotalPointsRepository totalPointsRepository;
    private TotalPointsServiceImp service;

    @BeforeEach
    void setUp() {
        totalPointsRepository = mock(TotalPointsRepository.class);
        service = new TotalPointsServiceImp(totalPointsRepository, new MockEnvironment());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousAuthenticationFallsBackToStudentEmail() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "test", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        service = new TotalPointsServiceImp(totalPointsRepository,
                new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://localhost/test"));
        TotalPoints created = TotalPoints.builder().student(student()).mentorship(mentorship()).totalPoints(0).build();
        when(totalPointsRepository.findForUpdate(5L, 100L))
                .thenReturn(Optional.empty(), Optional.of(created));
        service.applyDelta(student(), mentorship(), 1);
        verify(totalPointsRepository).insertEmptyIfAbsentPostgres(5L, 100L, "rahma@test.com");
        assertThat(created.getTotalPoints()).isEqualTo(1);
    }

    private static Student student() {
        return Student.builder().id(5L).firstName("Rahma").lastName("Ismail")
                .email("rahma@test.com").build();
    }

    private static MentorShip mentorship() {
        return MentorShip.builder().id(100L).title("Backend Track").build();
    }

    // ------------------------------------------------------------------
    // applyDelta behavior table
    // ------------------------------------------------------------------

    @Test
    @DisplayName("applyDelta(…, 0): early return — repository never touched")
    void zeroDelta_noRepositoryCalls() {
        service.applyDelta(student(), mentorship(), 0);

        verifyNoInteractions(totalPointsRepository);
    }

    @Test
    @DisplayName("positive delta, no existing row: creates a row whose total == delta")
    void positiveDelta_noRow_createsRowWithDelta() {
        when(totalPointsRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(TotalPoints.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service.applyDelta(student(), mentorship(), 15);

        ArgumentCaptor<TotalPoints> captor = ArgumentCaptor.forClass(TotalPoints.class);
        verify(totalPointsRepository, times(1)).saveAndFlush(captor.capture());
        TotalPoints saved = captor.getValue();
        assertThat(saved.getTotalPoints()).isEqualTo(15);
        assertThat(saved.getStudent().getId()).isEqualTo(5L);
        assertThat(saved.getMentorship().getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("positive delta mutates the locked existing row")
    void positiveDelta_existingRow_adds() {
        Student s = student();
        MentorShip m = mentorship();
        TotalPoints points = TotalPoints.builder().student(s).mentorship(m).totalPoints(10).build();
        when(totalPointsRepository.findForUpdate(5L, 100L)).thenReturn(Optional.of(points));

        service.applyDelta(s, m, 5);

        verify(totalPointsRepository).findForUpdate(5L, 100L);
        verify(totalPointsRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        assertThat(points.getTotalPoints()).isEqualTo(15);
    }

    @Test
    @DisplayName("negative delta mutates the locked existing row")
    void negativeDelta_partial_reduces() {
        TotalPoints points = TotalPoints.builder().student(student()).mentorship(mentorship()).totalPoints(10).build();
        when(totalPointsRepository.findForUpdate(5L, 100L)).thenReturn(Optional.of(points));

        service.applyDelta(student(), mentorship(), -3);

        verify(totalPointsRepository).findForUpdate(5L, 100L);
        verify(totalPointsRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        assertThat(points.getTotalPoints()).isEqualTo(7);
    }

    @Test
    @DisplayName("negative delta for an absent H2 row is floored at zero")
    void negativeDelta_floorsAtZero_neverNegative() {
        when(totalPointsRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(TotalPoints.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service.applyDelta(student(), mentorship(), -20);

        ArgumentCaptor<TotalPoints> captor = ArgumentCaptor.forClass(TotalPoints.class);
        verify(totalPointsRepository, times(1)).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTotalPoints()).isEqualTo(0);
        assertThat(captor.getValue().getTotalPoints()).isNotNegative();
    }
}
