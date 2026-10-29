package com.complyance.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Differential test: compares the event-driven scheduler with a deliberately naive reference that
 * advances virtual time one unit at a time and re-applies the specification's rules literally.
 * The reference shares no code with {@link JobScheduler}. Seeds are fixed, so runs are repeatable.
 */
class ReferenceSimulationTest {

    @Test
    void matchesTickByTickReferenceOnRandomDags() {
        Random random = new Random(20261026L);
        for (int round = 0; round < 1500; round++) {
            int taskCount = random.nextInt(13);          // 0..12 tasks
            int workers = 1 + random.nextInt(5);         // 1..5 workers
            List<Task> tasks = randomDag(random, taskCount);
            List<String> expected = referenceSchedule(tasks, workers);
            List<String> actual = TestSupport.render(JobScheduler.schedule(tasks, workers));
            assertEquals(expected, actual, "round " + round + " workers=" + workers + " tasks=" + tasks);
        }
    }

    /** Random IDs (mixed case, J10 vs J2 style) with dependencies only on earlier-generated tasks, then shuffled. */
    private static List<Task> randomDag(Random random, int count) {
        String[] pool = {"A", "B", "C", "D", "E", "J1", "J2", "J10", "a", "b", "Z", "x_1", "x-2", "Task9", "Task10"};
        List<String> ids = new ArrayList<>(List.of(pool));
        java.util.Collections.shuffle(ids, random);
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<String> dependencies = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                if (random.nextInt(4) == 0) {
                    dependencies.add(ids.get(j));
                }
            }
            tasks.add(new Task(ids.get(i), 1 + random.nextInt(4), random.nextInt(3), dependencies));
        }
        java.util.Collections.shuffle(tasks, random); // input order must not matter
        return tasks;
    }

    private static List<String> referenceSchedule(List<Task> tasks, int workers) {
        Map<String, Task> byId = new HashMap<>();
        tasks.forEach(t -> byId.put(t.id(), t));
        Set<String> started = new HashSet<>();
        Set<String> finished = new HashSet<>();
        String[] running = new String[workers + 1];   // task on each worker, null when idle
        int[] runningEnd = new int[workers + 1];
        List<int[]> startRecords = new ArrayList<>();  // {start, worker}
        List<String> startIds = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();

        for (int time = 0; finished.size() < tasks.size(); time++) {
            for (int worker = 1; worker <= workers; worker++) {          // 1. finish everything ending now
                if (running[worker] != null && runningEnd[worker] == time) {
                    finished.add(running[worker]);
                    running[worker] = null;
                }
            }
            List<Task> ready = new ArrayList<>();                         // 2-3. all ready tasks, sorted
            for (Task task : tasks) {
                if (!started.contains(task.id()) && finished.containsAll(task.dependencies())) {
                    ready.add(task);
                }
            }
            ready.sort(Comparator.comparingInt(Task::priority).thenComparing(Task::id));
            int next = 0;
            for (int worker = 1; worker <= workers && next < ready.size(); worker++) { // 4. lowest idle worker
                if (running[worker] == null) {
                    Task task = ready.get(next++);
                    running[worker] = task.id();
                    runningEnd[worker] = time + byId.get(task.id()).duration();
                    started.add(task.id());
                    startRecords.add(new int[] {time, worker});
                    startIds.add(task.id());
                    ends.add(runningEnd[worker]);
                }
            }
        }

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < startIds.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.<Integer>comparingInt(i -> startRecords.get(i)[0]).thenComparingInt(i -> startRecords.get(i)[1]));
        List<String> rendered = new ArrayList<>();
        for (int i : order) {
            rendered.add(startIds.get(i) + "@" + startRecords.get(i)[1] + "[" + startRecords.get(i)[0] + "-" + ends.get(i) + "]");
        }
        return rendered;
    }
}
