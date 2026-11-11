package com.example.gradproj.EduNest.service.points;

/**
 * EB-51 — Immutable result of an internal read-only reconciliation check.
 * This report is strictly for internal audit and verification; it does not mutate database state.
 */
public record PointsReconciliationReport(
        Long studentId,
        Long mentorshipId,
        int ledgerTotalPoints,
        int verifiedTasksPoints,
        int verifiedProjectsPoints,
        int verifiedQuizzesPoints,
        int verifiedBadgesPoints,
        int verifiedAttendancePoints,
        int totalVerifiablePoints,
        int unverifiedSubmissionsCount,
        ReconciliationClassification classification,
        String rationale
) {}
