package com.example.gradproj.EduNest.entity.email;

/**
 * EB-62 / INT-02: Status lifecycle of a failed email delivery attempt.
 */
public enum FailedEmailStatus {
    /**
     * Initial failure recorded, waiting for scheduled retry.
     */
    PENDING,

    /**
     * Successfully sent and delivered on a subsequent retry attempt.
     */
    DELIVERED,

    /**
     * Reached maximum allowed attempts without successful delivery.
     */
    EXHAUSTED,

    /**
     * Manually resolved or dismissed by an administrator.
     */
    RESOLVED
}
