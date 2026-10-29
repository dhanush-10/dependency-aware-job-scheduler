package com.complyance.scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Helpers for building tasks and rendering schedules compactly in assertions. */
final class TestSupport {
    private TestSupport() {
    }

    /** task("B", 2, 1, "A", "C") builds task B, duration 2, priority 1, depending on A and C. */
    static Task task(String id, int duration, int priority, String... dependencies) {
        return new Task(id, duration, priority, List.of(dependencies));
    }

    /** Renders each entry as "taskId@worker[start-end]" in output order. */
    static List<String> render(ScheduleResult result) {
        List<String> rows = new ArrayList<>();
        for (ScheduledTask entry : result.schedule()) {
            rows.add(entry.taskId() + "@" + entry.workerId() + "[" + entry.start() + "-" + entry.end() + "]");
        }
        return rows;
    }

    static List<String> rows(String... rows) {
        return Arrays.asList(rows);
    }

    static String csv(String... lines) {
        return "task_id,duration,priority,dependencies\n" + String.join("\n", lines) + (lines.length == 0 ? "" : "\n");
    }
}
