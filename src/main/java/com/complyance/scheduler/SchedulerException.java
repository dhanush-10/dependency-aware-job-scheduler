package com.complyance.scheduler;

import java.util.List;

/** A contract-level failure. It is rendered as the error JSON document; no schedule is produced. */
@SuppressWarnings("serial") // never serialized
public class SchedulerException extends Exception {
    private final ErrorCode code;
    private final List<String> cycle;

    public SchedulerException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public SchedulerException(ErrorCode code, String message, List<String> cycle) {
        super(message);
        this.code = code;
        this.cycle = cycle == null ? null : List.copyOf(cycle);
    }

    public ErrorCode code() {
        return code;
    }

    /** The closed cycle path (first ID repeated last), or null for non-cycle errors. */
    public List<String> cycle() {
        return cycle;
    }
}
