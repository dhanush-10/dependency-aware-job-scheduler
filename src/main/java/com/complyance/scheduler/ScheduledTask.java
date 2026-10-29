package com.complyance.scheduler;

/** One row of the output schedule. Times are virtual units and use long to avoid overflow. */
public record ScheduledTask(String taskId, int workerId, long start, long end) {
}
