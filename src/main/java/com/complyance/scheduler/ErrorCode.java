package com.complyance.scheduler;

/** Error codes defined by the output contract (SPECIFICATION.md section 5). */
public enum ErrorCode {
    INVALID_ARGUMENT,
    READ_ERROR,
    INVALID_CSV,
    INVALID_TASK,
    DUPLICATE_TASK,
    INVALID_DEPENDENCY,
    CYCLE_DETECTED
}
