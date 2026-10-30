package com.example.gradproj.EduNest.service.mentorShip;

import com.example.gradproj.EduNest.entity.mentorship.Commission;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.CommissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class MentorCommissionCalcTest {

    private CommissionRepository commissionRepository;
    private CommissionService service;

    @BeforeEach
    void setUp() {
        commissionRepository = mock(CommissionRepository.class);
        service = new CommissionService(commissionRepository);
    }

    private static Commission rate(double value) {
        return Commission.builder().rate(value).build();
    }

    // ------------------------------------------------------------------
    // Section A — rate lookup
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getCommissionRate: returns the latest stored rate (top by createdAt)")
    void getCommissionRate_present_returnsLatestRate() {
        when(commissionRepository.findTopByOrderByCreatedAtDesc())
                .thenReturn(Optional.of(rate(20.0)));

        assertThat(service.getCommissionRate()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("getCommissionRate: throws \"Commission rate not set\" when no rate exists")
    void getCommissionRate_notSet_throws() {
        when(commissionRepository.findTopByOrderByCreatedAtDesc())
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCommissionRate())
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Commission rate not set");
    }

    // ------------------------------------------------------------------
    // Section B — setCommissionRate validation (0..100 inclusive)
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "setCommissionRate accepts boundary {0}")
    @ValueSource(doubles = {0.0, 100.0})
    void setCommissionRate_boundaryValues_accepted(double boundary) {
        service.setCommissionRate(boundary);

        verify(commissionRepository).save(any());
    }

    @Test
    @DisplayName("setCommissionRate: negative rate rejected, nothing saved")
    void setCommissionRate_belowZero_rejected() {
        assertThatThrownBy(() -> service.setCommissionRate(-5.0))
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Commission rate must be between 0 and 100");

        verify(commissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("setCommissionRate: rate > 100 rejected, nothing saved")
    void setCommissionRate_aboveHundred_rejected() {
        assertThatThrownBy(() -> service.setCommissionRate(120.0))
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Commission rate must be between 0 and 100");

        verify(commissionRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Section C — platform cut / mentor payout split
    // ------------------------------------------------------------------

    @Test
    @DisplayName("calculatePlatformCut: 20% of 100 = 20.0")
    void platformCut_20pctOf100_is20() {
        when(commissionRepository.findTopByOrderByCreatedAtDesc())
                .thenReturn(Optional.of(rate(20.0)));

        assertThat(service.calculatePlatformCut(100.0)).isEqualTo(20.0);
    }

    @Test
    @DisplayName("calculatePlatformCut: throws when rate not set (getCommissionRate seam)")
    void platformCut_notSet_throws() {
        when(commissionRepository.findTopByOrderByCreatedAtDesc())
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculatePlatformCut(100.0))
                .isInstanceOf(globalLogicEx.class)
                .hasMessage("Commission rate not set");
    }

    @Test
    @DisplayName("calculateMentorPayout: 20% cut of 100 = mentor gets 80.0")
    void mentorPayout_20pctOf100_is80() {
        when(commissionRepository.findTopByOrderByCreatedAtDesc())
                .thenReturn(Optional.of(rate(20.0)));

        assertThat(service.calculateMentorPayout(100.0)).isEqualTo(80.0);
    }

    @ParameterizedTest(name = "cut + payout = price — price={0}, rate={1}")
    @ValueSource(doubles = {100.0, 250.0, 0.0})
    void cutPlusPayoutEqualsPrice_invariant(double price) {
        // Rate fixed at 12.5% so fractions exercise the double arithmetic.
        when(commissionRepository.findTopByOrderByCreatedAtDesc())
                .thenReturn(Optional.of(rate(12.5)));

        double cut = service.calculatePlatformCut(price);
        double payout = service.calculateMentorPayout(price);

        assertThat(cut + payout).isEqualTo(price);
        assertThat(cut).isEqualTo(price * (12.5 / 100.0));
    }
}
