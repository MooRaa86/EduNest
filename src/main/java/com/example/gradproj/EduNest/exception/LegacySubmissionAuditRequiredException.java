package com.example.gradproj.EduNest.exception;

import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;

/**
 * EB-51 D-09a: Thrown when attempting to regrade a historical submission whose points
 * provenance cannot be established from recorded row evidence without administrative audit.
 */
public class LegacySubmissionAuditRequiredException extends globalLogicEx {
    public LegacySubmissionAuditRequiredException(String message) {
        super(message);
    }
}
