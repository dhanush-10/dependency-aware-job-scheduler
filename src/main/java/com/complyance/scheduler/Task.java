package com.complyance.scheduler;

import java.util.List;

/** A validated unit of work. Dependencies are task IDs, in the order they were written. */
public record Task(String id, int duration, int priority, List<String> dependencies) {
}
