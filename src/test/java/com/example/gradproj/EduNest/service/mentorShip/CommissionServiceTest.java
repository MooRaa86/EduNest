package com.example.gradproj.EduNest.service.mentorShip;

import com.example.gradproj.EduNest.entity.mentorship.Commission;
import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.repository.mentorShip.CommissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EB-47 / P3-05: CommissionService Unit Tests & Defensive Hardening")
class CommissionServiceTest {

    @Mock
    private CommissionRepository commissionRepository;

    private CommissionService commissionService;

    @BeforeEach
    void setUp() {
        commissionService = new CommissionService(commissionRepository);
    }

    @Nested
    @DisplayName("getCommissionRate")
    class GetCommissionRateTests {

        @Test
        @DisplayName("Returns latest valid commission rate when present")
        void returnsLatestValidRate() {
            Commission commission = Commission.builder().rate(10.0).build();
            when(commissionRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.of(commission));

            Double rate = commissionService.getCommissionRate();

            assertThat(rate).isEqualTo(10.0);
        }

        @Test
        @DisplayName("Throws globalLogicEx when commission table is empty (MSH-05 / genuine misconfiguration)")
        void throwsWhenTableIsEmpty() {
            when(commissionRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> commissionService.getCommissionRate())
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessage("Commission rate not set");
        }

        @Test
        @DisplayName("Throws globalLogicEx when rate column in DB row is null")
        void throwsWhenRateIsNull() {
            Commission commission = Commission.builder().rate(null).build();
            when(commissionRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.of(commission));

            assertThatThrownBy(() -> commissionService.getCommissionRate())
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessage("Commission rate not set");
        }

        @Test
        @DisplayName("Throws globalLogicEx when rate in DB row is negative or out of bounds")
        void throwsWhenRateIsOutOfBounds() {
            Commission commissionNegative = Commission.builder().rate(-5.0).build();
            when(commissionRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.of(commissionNegative));

            assertThatThrownBy(() -> commissionService.getCommissionRate())
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessage("Commission rate not set");

            Commission commissionOver100 = Commission.builder().rate(105.0).build();
            when(commissionRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.of(commissionOver100));

            assertThatThrownBy(() -> commissionService.getCommissionRate())
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessage("Commission rate not set");
        }
    }

    @Nested
    @DisplayName("setCommissionRate (Admin dynamic update)")
    class SetCommissionRateTests {

        @Test
        @DisplayName("Saves new commission entity with specified valid rate")
        void savesValidRate() {
            commissionService.setCommissionRate(15.0);

            ArgumentCaptor<Commission> captor = ArgumentCaptor.forClass(Commission.class);
            verify(commissionRepository).save(captor.capture());

            Commission saved = captor.getValue();
            assertThat(saved.getRate()).isEqualTo(15.0);
        }

        @Test
        @DisplayName("Rejects null rate with descriptive message")
        void rejectsNullRate() {
            assertThatThrownBy(() -> commissionService.setCommissionRate(null))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessage("Commission rate must be between 0 and 100");
        }

        @Test
        @DisplayName("Rejects rate < 0")
        void rejectsNegativeRate() {
            assertThatThrownBy(() -> commissionService.setCommissionRate(-0.1))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessage("Commission rate must be between 0 and 100");
        }

        @Test
        @DisplayName("Rejects rate > 100")
        void rejectsRateOver100() {
            assertThatThrownBy(() -> commissionService.setCommissionRate(100.1))
                    .isInstanceOf(globalLogicEx.class)
                    .hasMessage("Commission rate must be between 0 and 100");
        }
    }

    @Nested
    @DisplayName("calculatePlatformCut and calculateMentorPayout")
    class MoneyCalculationTests {

        @Test
        @DisplayName("Calculates platform cut correctly with valid price and 10% rate")
        void calculatesPlatformCutCorrectly() {
            when(commissionRepository.findTopByOrderByCreatedAtDesc())
                    .thenReturn(Optional.of(Commission.builder().rate(10.0).build()));

            Double platformCut = commissionService.calculatePlatformCut(200.0);

            assertThat(platformCut).isEqualTo(20.0, within(1e-9));
        }

        @Test
        @DisplayName("Calculates mentor payout correctly with valid price and 10% rate")
        void calculatesMentorPayoutCorrectly() {
            when(commissionRepository.findTopByOrderByCreatedAtDesc())
                    .thenReturn(Optional.of(Commission.builder().rate(10.0).build()));

            Double mentorPayout = commissionService.calculateMentorPayout(200.0);

            assertThat(mentorPayout).isEqualTo(180.0, within(1e-9));
        }

        @Test
        @DisplayName("Returns 0.0 for platform cut when price is null, zero, or negative")
        void returnsZeroForInvalidPriceOnPlatformCut() {
            when(commissionRepository.findTopByOrderByCreatedAtDesc())
                    .thenReturn(Optional.of(Commission.builder().rate(10.0).build()));

            assertThat(commissionService.calculatePlatformCut(null)).isZero();
            assertThat(commissionService.calculatePlatformCut(0.0)).isZero();
            assertThat(commissionService.calculatePlatformCut(-100.0)).isZero();
        }

        @Test
        @DisplayName("Returns 0.0 for mentor payout when price is null, zero, or negative")
        void returnsZeroForInvalidPriceOnMentorPayout() {
            assertThat(commissionService.calculateMentorPayout(null)).isZero();
            assertThat(commissionService.calculateMentorPayout(0.0)).isZero();
            assertThat(commissionService.calculateMentorPayout(-50.0)).isZero();
        }
    }
}
