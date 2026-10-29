package com.complyance.scheduler;

import static com.complyance.scheduler.TestSupport.render;
import static com.complyance.scheduler.TestSupport.rows;
import static com.complyance.scheduler.TestSupport.task;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobSchedulerTest {

    @Test
    void assignsByPriorityDependenciesAndLowestWorker_assignmentExample() {
        List<Task> tasks = List.of(
                task("A", 3, 2), task("B", 2, 1, "A"), task("C", 4, 1), task("D", 1, 2, "B", "C"));
        ScheduleResult result = JobScheduler.schedule(tasks, 2);
        assertEquals(6, result.makespan());
        assertEquals(rows("C@1[0-4]", "A@2[0-3]", "B@2[3-5]", "D@1[5-6]"), render(result));
    }

    @Test
    void smallerPriorityNumberRunsFirstRegardlessOfInputOrder() {
        List<Task> tasks = new ArrayList<>(List.of(task("A", 1, 5), task("B", 1, 0), task("C", 1, 3)));
        for (int shuffle = 0; shuffle < 6; shuffle++) {
            Collections.rotate(tasks, 1);
            if (shuffle % 2 == 1) {
                Collections.reverse(tasks);
            }
            assertEquals(rows("B@1[0-1]", "C@1[1-2]", "A@1[2-3]"), render(JobScheduler.schedule(tasks, 1)));
        }
    }

    @Test
    void equalPriorityBreaksTiesByAsciiTaskId_notNaturalOrderOrCase() {
        // ASCII: "J10" < "J2" (since '1' < '2'), and uppercase "Z" < lowercase "a".
        List<Task> tasks = List.of(task("J2", 1, 1), task("a", 1, 1), task("J10", 1, 1), task("Z", 1, 1));
        assertEquals(rows("J10@1[0-1]", "J2@1[1-2]", "Z@1[2-3]", "a@1[3-4]"),
                render(JobScheduler.schedule(tasks, 1)));
    }

    @Test
    void tieBreakingDoesNotDependOnInputRowOrder() {
        List<Task> forward = List.of(task("A", 2, 1), task("B", 2, 1), task("C", 2, 1));
        List<Task> backward = List.of(task("C", 2, 1), task("B", 2, 1), task("A", 2, 1));
        assertEquals(render(JobScheduler.schedule(forward, 2)), render(JobScheduler.schedule(backward, 2)));
        assertEquals(rows("A@1[0-2]", "B@2[0-2]", "C@1[2-4]"), render(JobScheduler.schedule(backward, 2)));
    }

    @Test
    void taskWithMultipleDependenciesWaitsForTheLastOne() {
        List<Task> tasks = List.of(task("A", 1, 0), task("B", 5, 0), task("C", 1, 0), task("X", 1, 0, "A", "B", "C"));
        ScheduleResult result = JobScheduler.schedule(tasks, 3);
        assertEquals(rows("A@1[0-1]", "B@2[0-5]", "C@3[0-1]", "X@1[5-6]"), render(result));
    }

    @Test
    void simultaneousCompletionsAreAllProcessedBeforeSelection() {
        // A and B both finish at 2. E (needs A and B, priority 0) must beat D and C at t=2,
        // and D (priority 1) must beat C (priority 2) even though C became ready from A.
        List<Task> tasks = List.of(
                task("A", 2, 2), task("B", 2, 1), task("C", 1, 2, "A"), task("D", 1, 1, "B"),
                task("E", 1, 0, "A", "B"), task("F", 2, 3));
        ScheduleResult result = JobScheduler.schedule(tasks, 2);
        assertEquals(rows("B@1[0-2]", "A@2[0-2]", "E@1[2-3]", "D@2[2-3]", "C@1[3-4]", "F@2[3-5]"), render(result));
        assertEquals(5, result.makespan());
    }

    @Test
    void newlyReadyTasksAreSortedTogetherWithTasksAlreadyWaiting() {
        // One worker. Waiting: W (priority 5). At t=1 task N (priority 0) becomes ready and must jump the queue.
        List<Task> tasks = List.of(task("S", 1, 0), task("W", 1, 5), task("N", 1, 0, "S"));
        assertEquals(rows("S@1[0-1]", "N@1[1-2]", "W@1[2-3]"), render(JobScheduler.schedule(tasks, 1)));
    }

    @Test
    void idleWorkerFromAnEarlierInstantIsUsedWhenItIsTheLowestNumbered() {
        // Worker 1 finishes at 1 and idles; worker 2 finishes at 3. At t=3 task C becomes ready and
        // must go to worker 1 (idle since t=1), not to worker 2 which just finished.
        List<Task> tasks = List.of(task("A", 1, 1), task("B", 3, 1), task("C", 2, 0, "B"));
        ScheduleResult result = JobScheduler.schedule(tasks, 3);
        assertEquals(rows("A@1[0-1]", "B@2[0-3]", "C@1[3-5]"), render(result));
    }

    @Test
    void idlePeriodsAreAllowedWhenNothingIsReady() {
        List<Task> tasks = List.of(task("A", 10, 0), task("B", 1, 0, "A"));
        ScheduleResult result = JobScheduler.schedule(tasks, 4);
        assertEquals(rows("A@1[0-10]", "B@1[10-11]"), render(result));
        assertEquals(11, result.makespan());
    }

    @Test
    void doesNotWaitForABlockedHigherPriorityTask() {
        // H (priority 0) is blocked behind a long task; the low-priority L must still start at 0.
        List<Task> tasks = List.of(task("LONG", 10, 9), task("H", 1, 0, "LONG"), task("L", 3, 9));
        ScheduleResult result = JobScheduler.schedule(tasks, 2);
        assertEquals(rows("L@1[0-3]", "LONG@2[0-10]", "H@1[10-11]"), render(result));
    }

    @Test
    void emptyTaskListGivesZeroMakespanAndEmptySchedule() {
        ScheduleResult result = JobScheduler.schedule(List.of(), 5);
        assertEquals(0, result.makespan());
        assertEquals(5, result.workers());
        assertTrue(result.schedule().isEmpty());
    }

    @Test
    void moreWorkersThanTasksLeavesHigherWorkersUnused() {
        ScheduleResult result = JobScheduler.schedule(List.of(task("A", 2, 0), task("B", 3, 0)), 32);
        assertEquals(rows("A@1[0-2]", "B@2[0-3]"), render(result));
        assertEquals(32, result.workers());
    }

    @Test
    void largestAllowedDurationsDoNotOverflowOrTakeRealTime() {
        // 2,000 chained tasks of 1,000,000 units: makespan is 2,000,000,000, close to the int limit,
        // so this checks long arithmetic; a time-stepping loop would not finish inside the timeout.
        List<Task> chain = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            chain.add(i == 0 ? task("T0", 1_000_000, 0) : task("T" + i, 1_000_000, 0, "T" + (i - 1)));
        }
        ScheduleResult result = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> JobScheduler.schedule(chain, 3));
        assertEquals(2_000_000_000L, result.makespan());
        assertEquals(2000, result.schedule().size());
        assertEquals(1_999_000_000L, result.schedule().get(1999).start());
    }

    @Test
    void largeIndependentWorkloadFinishesQuickly() {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            tasks.add(task("J" + i, 1 + (i % 7), i % 5));
        }
        ScheduleResult result = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> JobScheduler.schedule(tasks, 32));
        assertEquals(2000, result.schedule().size());
        assertTrue(result.makespan() > 0);
    }
}
