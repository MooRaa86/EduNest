package com.example.gradproj.EduNest.exception;

import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;

/** A conflict whose transaction must be retried from a fresh snapshot. */
public class ConcurrentModificationConflict extends globalLogicEx {
    public ConcurrentModificationConflict(String message) {
        super(message);
    }
}
