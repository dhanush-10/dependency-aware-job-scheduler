package com.complyance.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds a dependency cycle with an iterative depth-first search (no recursion, so a 2,000-task
 * chain cannot overflow the stack). Tasks and dependencies are visited in ASCII order so the
 * reported cycle is deterministic. Runs in O(V + E).
 */
final class CycleDetector {
    private enum Mark { IN_PROGRESS, DONE }

    private CycleDetector() {
    }

    static void requireAcyclic(List<Task> tasks) throws SchedulerException {
        Map<String, List<String>> dependenciesById = new HashMap<>();
        for (Task task : tasks) {
            List<String> sorted = new ArrayList<>(task.dependencies());
            Collections.sort(sorted);
            dependenciesById.put(task.id(), sorted);
        }
        List<String> startOrder = new ArrayList<>(dependenciesById.keySet());
        Collections.sort(startOrder);

        Map<String, Mark> marks = new HashMap<>();
        for (String start : startOrder) {
            if (marks.containsKey(start)) {
                continue;
            }
            List<String> path = new ArrayList<>();
            List<Integer> nextChild = new ArrayList<>();
            path.add(start);
            nextChild.add(0);
            marks.put(start, Mark.IN_PROGRESS);

            while (!path.isEmpty()) {
                int top = path.size() - 1;
                String current = path.get(top);
                List<String> dependencies = dependenciesById.get(current);
                int index = nextChild.get(top);
                if (index == dependencies.size()) {
                    marks.put(current, Mark.DONE);
                    path.remove(top);
                    nextChild.remove(top);
                    continue;
                }
                nextChild.set(top, index + 1);
                String dependency = dependencies.get(index);
                Mark mark = marks.get(dependency);
                if (mark == Mark.IN_PROGRESS) {
                    throw cycleFrom(path, dependency);
                }
                if (mark == null) {
                    marks.put(dependency, Mark.IN_PROGRESS);
                    path.add(dependency);
                    nextChild.add(0);
                }
            }
        }
    }

    /** Builds the closed path: every next ID is a dependency of the previous, last repeats first. */
    private static SchedulerException cycleFrom(List<String> path, String repeated) {
        List<String> cycle = new ArrayList<>(path.subList(path.indexOf(repeated), path.size()));
        cycle.add(repeated);
        return new SchedulerException(ErrorCode.CYCLE_DETECTED,
                "Dependency cycle " + String.join(" -> ", cycle) + " (each task depends on the next)", cycle);
    }
}
