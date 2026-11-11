package com.example.gradproj.EduNest.service.points;

/**
 * EB-51 — Evidence-based classifications for points ledger reconciliation.
 *
 * <ul>
 *   <li>{@link #EXACT}: Every historical contribution is backed by authoritative recorded evidence
 *       (e.g. points_applied is non-null for all graded deliverables, completed quizzes, badges,
 *       and attendance), and the sum matches the ledger balance exactly.</li>
 *   <li>{@link #PARTIAL}: Some contributions have reliable recorded evidence, but an unexplained
 *       delta remains between the verifiable sum and the TotalPoints ledger balance, or legacy graded
 *       submissions exist where points_applied was unpopulated.</li>
 *   <li>{@link #UNRECONSTRUCTABLE}: Submissions exist with ambiguous history (e.g. resubmission erased
 *       score evidence, or points_applied is null without verifiable grading provenance), making it
 *       impossible to establish or exclude earlier credit.</li>
 * </ul>
 */
public enum ReconciliationClassification {
    EXACT,
    PARTIAL,
    UNRECONSTRUCTABLE
}
