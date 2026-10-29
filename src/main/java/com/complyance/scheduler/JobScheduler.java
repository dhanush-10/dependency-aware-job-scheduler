package com.complyance.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeSet;

/**
 * Event-driven simulation of the scheduling policy in SPECIFICATION.md section 3.
 *
 * <p>Time jumps from one completion instant to the next, so the cost is independent of the task
 * durations: O(n log n + e) time for n tasks and e dependency edges, O(n + e + W) space.
 */
public final class JobScheduler {
    /** Ready tasks: smaller priority number first, then task ID in ASCII (char code) order. */
    private static final Comparator<Task> READY_ORDER =
            Comparator.comparingInt(Task::priority).thenComparing(Task::id);

    private record RunningTask(Task task, int workerId, long end) {
    }

    private JobScheduler() {
    }

    /** Schedules validated, acyclic tasks on {@code workerCount} workers (IDs 1..workerCount). */
    public static ScheduleResult schedule(List<Task> tasks, int workerCount) {
        Map<String, Integer> unfinishedDependencies = new HashMap<>();
        Map<String, List<Task>> dependents = new HashMap<>();
        PriorityQueue<Task> ready = new PriorityQueue<>(READY_ORDER);
        for (Task task : tasks) {
            unfinishedDependencies.put(task.id(), task.dependencies().size());
            for (String dependency : task.dependencies()) {
                dependents.computeIfAbsent(dependency, key -> new ArrayList<>()).add(task);
            }
            if (task.dependencies().isEmpty()) {
                ready.add(task);
            }
        }

        TreeSet<Integer> idleWorkers = new TreeSet<>(); // lowest worker ID first
        for (int workerId = 1; workerId <= workerCount; workerId++) {
            idleWorkers.add(workerId);
        }
        PriorityQueue<RunningTask> running = new PriorityQueue<>(Comparator.comparingLong(RunningTask::end));

        List<ScheduledTask> schedule = new ArrayList<>(tasks.size());
        long now = 0;
        int finished = 0;
        while (finished < tasks.size()) {
            // Steps 3-4: sort the whole ready set and give it to idle workers, lowest ID first.
            while (!ready.isEmpty() && !idleWorkers.isEmpty()) {
                Task task = ready.poll();
                int workerId = idleWorkers.pollFirst();
                long end = now + task.duration();
                running.add(new RunningTask(task, workerId, end));
                schedule.add(new ScheduledTask(task.id(), workerId, now, end));
            }
            if (running.isEmpty()) {
                // Unreachable for validated input: an acyclic graph always has a ready task.
                throw new IllegalStateException("No runnable task but " + (tasks.size() - finished) + " unfinished");
            }
            // Step 5 then step 1: jump to the next completion and finish ALL tasks ending then.
            now = running.peek().end();
            while (!running.isEmpty() && running.peek().end() == now) {
                RunningTask done = running.poll();
                idleWorkers.add(done.workerId());
                finished++;
                // Step 2: release dependents whose last dependency just finished.
                for (Task dependent : dependents.getOrDefault(done.task().id(), List.of())) {
                    if (unfinishedDependencies.merge(dependent.id(), -1, Integer::sum) == 0) {
                        ready.add(dependent);
                    }
                }
            }
        }

        schedule.sort(Comparator.comparingLong(ScheduledTask::start).thenComparingInt(ScheduledTask::workerId));
        return new ScheduleResult(workerCount, now, schedule);
    }
}
