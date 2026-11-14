package com.example.gradproj.EduNest.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4-09 / EB-58 — unit matrix for the shared pagination clamp policy.
 * Pure Java — no Spring context, engine-independent.
 */
@Tag("critical")
class PaginationPolicyTest {

    @ParameterizedTest(name = "page={0} → clamped page")
    @ValueSource(ints = {-5, -1})
    @DisplayName("P4-09: negative page clamps to 0")
    void negativePage_clampsToZero(int page) {
        Pageable pageable = PaginationPolicy.ofClamped(page, 10);
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
    }

    @ParameterizedTest(name = "size={0} → clamped size 1")
    @ValueSource(ints = {0, -3, Integer.MIN_VALUE})
    @DisplayName("P4-09: zero/negative size clamps to 1 (never an IllegalArgumentException → 500)")
    void nonPositiveSize_clampsToOne(int size) {
        Pageable pageable = PaginationPolicy.ofClamped(0, size);
        assertThat(pageable.getPageSize()).isEqualTo(1);
        assertThat(pageable.getPageNumber()).isZero();
    }

    @ParameterizedTest(name = "size={0} → clamped to MAX_PAGE_SIZE")
    @ValueSource(ints = {51, 100, 10_000, Integer.MAX_VALUE})
    @DisplayName("P4-09: oversized size clamps to MAX_PAGE_SIZE=50 (API-02 unbounded fix)")
    void oversizedSize_clampsToMax(int size) {
        Pageable pageable = PaginationPolicy.ofClamped(0, size);
        assertThat(pageable.getPageSize()).isEqualTo(PaginationPolicy.MAX_PAGE_SIZE);
    }

    @ParameterizedTest(name = "page={0}, size={1} → page {2}, size {3}")
    @CsvSource({
            "0,   1,  0,  1",
            "0,  10,  0, 10",
            "0,  50,  0, 50",
            "2,  20,  2, 20",
            "7,  49,  7, 49"
    })
    @DisplayName("P4-09: normal client values pass through unchanged (FE compatibility)")
    void normalValues_unchanged(int page, int size, int expectedPage, int expectedSize) {
        Pageable pageable = PaginationPolicy.ofClamped(page, size);
        assertThat(pageable.getPageNumber()).isEqualTo(expectedPage);
        assertThat(pageable.getPageSize()).isEqualTo(expectedSize);
    }

    @Test
    @DisplayName("P4-09: sort overload preserves server-defined Sort")
    void sortOverload_preservesSort() {
        Sort sort = Sort.by("createdAt").descending();
        Pageable pageable = PaginationPolicy.ofClamped(-1, 10_000, sort);
        assertThat(pageable.getSort()).isEqualTo(sort);
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(PaginationPolicy.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("P4-09 (approved exception A): notification cap stays 100 — size=100 → 100, size=101 → 100, size=5000 → 100")
    void notificationCap_is100() {
        assertThat(PaginationPolicy.clampSize(100, PaginationPolicy.NOTIFICATION_MAX_PAGE_SIZE)).isEqualTo(100);
        assertThat(PaginationPolicy.clampSize(101, PaginationPolicy.NOTIFICATION_MAX_PAGE_SIZE)).isEqualTo(100);
        assertThat(PaginationPolicy.clampSize(5_000, PaginationPolicy.NOTIFICATION_MAX_PAGE_SIZE)).isEqualTo(100);
        assertThat(PaginationPolicy.clampSize(0, PaginationPolicy.NOTIFICATION_MAX_PAGE_SIZE)).isEqualTo(1);
    }

    @Test
    @DisplayName("P4-09: constants locked per approved decisions")
    void constants_locked() {
        assertThat(PaginationPolicy.MAX_PAGE_SIZE).isEqualTo(50);
        assertThat(PaginationPolicy.NOTIFICATION_MAX_PAGE_SIZE).isEqualTo(100);
        assertThat(PaginationPolicy.MIN_PAGE_SIZE).isEqualTo(1);
    }
}
