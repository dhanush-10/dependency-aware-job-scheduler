package com.complyance.scheduler;

import java.util.List;

public record ScheduleResult(int workers, long makespan, List<ScheduledTask> schedule) {
}
