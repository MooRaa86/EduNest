package com.example.gradproj.EduNest.service.points;

import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.points.TotalPoints;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.points.TotalPointsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
        service = new TotalPointsServiceImp(totalPointsRepository);
    }

    private static Student student() {
        return Student.builder().id(5L).firstName("Rahma").lastName("Ismail")
                .email("rahma@test.com").build();
    }

    private static MentorShip mentorship() {
        return MentorShip.builder().id(100L).title("Backend Track").build();
    }

    private static TotalPoints existing(int total) {
        return TotalPoints.builder().student(student()).mentorship(mentorship())
                .totalPoints(total).build();
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
        when(totalPointsRepository.findForUpdate(5L, 100L)).thenReturn(Optional.empty());

        service.applyDelta(student(), mentorship(), 15);

        ArgumentCaptor<TotalPoints> captor = ArgumentCaptor.forClass(TotalPoints.class);
        verify(totalPointsRepository, times(1)).save(captor.capture());
        TotalPoints saved = captor.getValue();
        assertThat(saved.getTotalPoints()).isEqualTo(15);
        assertThat(saved.getStudent().getId()).isEqualTo(5L);
        assertThat(saved.getMentorship().getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("positive delta, existing row: total increases by delta (15 <- 10 + 5)")
    void positiveDelta_existingRow_adds() {
        Student s = student();
        MentorShip m = mentorship();
        TotalPoints tp = existing(10);
        when(totalPointsRepository.findForUpdate(5L, 100L)).thenReturn(Optional.of(tp));

        service.applyDelta(s, m, 5);

        ArgumentCaptor<TotalPoints> captor = ArgumentCaptor.forClass(TotalPoints.class);
        verify(totalPointsRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTotalPoints()).isEqualTo(15);
    }

    @Test
    @DisplayName("negative delta that stays positive: total decreases (7 <- 10 - 3)")
    void negativeDelta_partial_reduces() {
        TotalPoints tp = existing(10);
        when(totalPointsRepository.findForUpdate(5L, 100L)).thenReturn(Optional.of(tp));

        service.applyDelta(student(), mentorship(), -3);

        ArgumentCaptor<TotalPoints> captor = ArgumentCaptor.forClass(TotalPoints.class);
        verify(totalPointsRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTotalPoints()).isEqualTo(7);
    }

    @Test
    @DisplayName("negative delta beyond total: 0-clamp floors at zero, never negative (GAME-05 accepted model)")
    void negativeDelta_floorsAtZero_neverNegative() {
        TotalPoints tp = existing(5);
        when(totalPointsRepository.findForUpdate(5L, 100L)).thenReturn(Optional.of(tp));

        service.applyDelta(student(), mentorship(), -20);

        ArgumentCaptor<TotalPoints> captor = ArgumentCaptor.forClass(TotalPoints.class);
        verify(totalPointsRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTotalPoints()).isEqualTo(0);
        assertThat(captor.getValue().getTotalPoints()).isNotNegative();
    }
}
