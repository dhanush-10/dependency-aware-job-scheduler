package com.complyance.scheduler;

import static com.complyance.scheduler.TestSupport.csv;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskLoaderTest {

    private static SchedulerException failure(String csvText) {
        return assertThrows(SchedulerException.class, () -> TaskLoader.parse(csvText));
    }

    private static void assertCode(ErrorCode expected, String csvText) {
        SchedulerException error = failure(csvText);
        assertEquals(expected, error.code(), error.getMessage());
        assertFalse(error.getMessage().isBlank());
    }

    // ---- valid input ----

    @Test
    void parsesTasksAndForwardReferences() throws SchedulerException {
        List<Task> tasks = TaskLoader.parse(csv("B,2,1,A", "A,3,2,"));
        assertEquals(2, tasks.size());
        assertEquals(new Task("B", 2, 1, List.of("A")), tasks.get(0));
        assertEquals(new Task("A", 3, 2, List.of()), tasks.get(1));
    }

    @Test
    void headerOnlyFileIsAValidEmptyTaskSet() throws SchedulerException {
        assertTrue(TaskLoader.parse("task_id,duration,priority,dependencies\n").isEmpty());
        assertTrue(TaskLoader.parse("task_id,duration,priority,dependencies").isEmpty());
    }

    @Test
    void acceptsBomCrlfBlankLinesAndWhitespaceTrimming() throws SchedulerException {
        String text = "\uFEFF task_id , duration,priority ,dependencies\r\n\r\n  \r\n"
                + " A , 3 , 2 , \r\n\"B\", 2 ,1,\" A \"\r\n\r\n";
        List<Task> tasks = TaskLoader.parse(text);
        assertEquals(List.of(new Task("A", 3, 2, List.of()), new Task("B", 2, 1, List.of("A"))), tasks);
    }

    @Test
    void trimsWhitespaceAroundEachDependencyToken() throws SchedulerException {
        List<Task> tasks = TaskLoader.parse(csv("A,1,0,", "B,1,0,", "C,1,0,\" A , B \""));
        assertEquals(List.of("A", "B"), tasks.get(2).dependencies());
    }

    @Test
    void acceptsLeadingZeroesAndBoundaryValues() throws SchedulerException {
        List<Task> tasks = TaskLoader.parse(csv("A,0001000000,0000,", "a_b-9,1,1000,"));
        assertEquals(1_000_000, tasks.get(0).duration());
        assertEquals(0, tasks.get(0).priority());
        assertEquals(1000, tasks.get(1).priority());
    }

    @Test
    void acceptsThirtyTwoCharacterIdAndExactly2000Tasks() throws SchedulerException {
        String longestId = "A" + "b".repeat(31);
        assertEquals(longestId, TaskLoader.parse(csv(longestId + ",1,0,")).get(0).id());
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            lines.add("T" + i + ",1,0,");
        }
        assertEquals(2000, TaskLoader.parse(csv(lines.toArray(String[]::new))).size());
    }

    @Test
    void whitespaceOnlySingleFieldRecordsAreIgnored() throws SchedulerException {
        assertEquals(1, TaskLoader.parse(csv("", "   ", "A,1,0,", "\t", "")).size());
    }

    // ---- CSV level ----

    @Test
    void rejectsMissingOrWrongHeader() {
        assertCode(ErrorCode.INVALID_CSV, "");
        assertCode(ErrorCode.INVALID_CSV, "\n  \n");
        assertCode(ErrorCode.INVALID_CSV, "A,1,0,\n");
        assertCode(ErrorCode.INVALID_CSV, "task_id,priority,duration,dependencies\nA,1,0,\n");
        assertCode(ErrorCode.INVALID_CSV, "TASK_ID,duration,priority,dependencies\nA,1,0,\n");
        assertCode(ErrorCode.INVALID_CSV, "task_id,duration,priority\nA,1,0\n");
        assertCode(ErrorCode.INVALID_CSV, "task_id,duration,priority,dependencies,extra\nA,1,0,,x\n");
    }

    @Test
    void rejectsRowsWithWrongColumnCount() {
        assertCode(ErrorCode.INVALID_CSV, csv("A,1,0"));
        assertCode(ErrorCode.INVALID_CSV, csv("A,1,0,,"));
        assertCode(ErrorCode.INVALID_CSV, csv("A,1,0,B,C")); // unquoted comma list is an extra column
    }

    @Test
    void rejectsMalformedCsvAndInvalidUtf8() {
        assertCode(ErrorCode.INVALID_CSV, csv("A,1,0,\"B"));
        byte[] invalidUtf8 = {'t', 'a', 's', 'k', '_', 'i', 'd', (byte) 0xC3, (byte) 0x28};
        SchedulerException error = assertThrows(SchedulerException.class, () -> TaskLoader.parse(invalidUtf8));
        assertEquals(ErrorCode.INVALID_CSV, error.code());
    }

    // ---- task fields ----

    @Test
    void rejectsInvalidTaskIds() {
        for (String id : List.of("", "1A", "_A", "-A", "A B", "A.B", "A".repeat(33), "\u00C9clair")) {
            assertCode(ErrorCode.INVALID_TASK, csv(id + ",1,0,"));
        }
    }

    @Test
    void rejectsInvalidDurations() {
        for (String duration : List.of("", "0", "000", "-1", "+1", "1.0", "1e3", "abc", "1000001", "99999999999999999999", "1 2")) {
            assertCode(ErrorCode.INVALID_TASK, csv("A," + duration + ",0,"));
        }
    }

    @Test
    void rejectsInvalidPriorities() {
        for (String priority : List.of("", "-1", "+1", "1.5", "x", "1001", "99999999999999999999")) {
            assertCode(ErrorCode.INVALID_TASK, csv("A,1," + priority + ","));
        }
    }

    @Test
    void rejectsAllEmptyFieldRowAsInvalidTaskNotBlankLine() {
        assertCode(ErrorCode.INVALID_TASK, csv(",,,"));
    }

    @Test
    void rejectsMoreThan2000Tasks() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 2001; i++) {
            lines.add("T" + i + ",1,0,");
        }
        assertCode(ErrorCode.INVALID_TASK, csv(lines.toArray(String[]::new)));
    }

    @Test
    void rejectsDuplicateTaskIdsIncludingCaseSensitiveExactMatchOnly() throws SchedulerException {
        assertCode(ErrorCode.DUPLICATE_TASK, csv("A,1,0,", "A,2,1,"));
        assertCode(ErrorCode.DUPLICATE_TASK, csv("A,1,0,", "B,1,0,", " A ,2,1,"));
        assertEquals(2, TaskLoader.parse(csv("A,1,0,", "a,1,0,")).size()); // IDs are case-sensitive
    }

    // ---- dependencies ----

    @Test
    void rejectsUnknownDependency() {
        SchedulerException error = failure(csv("A,1,0,MISSING"));
        assertEquals(ErrorCode.INVALID_DEPENDENCY, error.code());
        assertTrue(error.getMessage().contains("MISSING"));
    }

    @Test
    void rejectsDependencyIdsAreCaseSensitive() {
        assertCode(ErrorCode.INVALID_DEPENDENCY, csv("A,1,0,", "B,1,0,a"));
    }

    @Test
    void rejectsSelfDependency() {
        assertCode(ErrorCode.INVALID_DEPENDENCY, csv("A,1,0,A"));
    }

    @Test
    void rejectsRepeatedDependency() {
        assertCode(ErrorCode.INVALID_DEPENDENCY, csv("A,1,0,", "B,1,0,\"A,A\""));
        assertCode(ErrorCode.INVALID_DEPENDENCY, csv("A,1,0,", "B,1,0,\"A, A \""));
    }

    @Test
    void rejectsEmptyAndMalformedDependencyTokens() {
        String base = "A,1,0,";
        assertCode(ErrorCode.INVALID_DEPENDENCY, csv(base, "B,1,0,\"A,\""));
        assertCode(ErrorCode.INVALID_DEPENDENCY, csv(base, "B,1,0,\",A\""));
        assertCode(ErrorCode.INVALID_DEPENDENCY, csv(base, "B,1,0,\"A,,A\""));
        assertCode(ErrorCode.INVALID_DEPENDENCY, csv(base, "B,1,0,\"A, ,\""));
        assertCode(ErrorCode.INVALID_DEPENDENCY, csv(base, "B,1,0,\"A;C\""));
        assertCode(ErrorCode.INVALID_DEPENDENCY, csv(base, "B,1,0,\"1A\""));
    }

    // ---- cycles ----

    private static void assertValidCycle(SchedulerException error, List<Task> byId) {
        List<String> cycle = error.cycle();
        assertEquals(ErrorCode.CYCLE_DETECTED, error.code());
        assertTrue(cycle.size() >= 3, "cycle needs at least two distinct tasks and a repeated first: " + cycle);
        assertEquals(cycle.get(0), cycle.get(cycle.size() - 1));
        for (int i = 0; i + 1 < cycle.size(); i++) {
            String from = cycle.get(i);
            String to = cycle.get(i + 1);
            Task task = byId.stream().filter(t -> t.id().equals(from)).findFirst().orElseThrow();
            assertTrue(task.dependencies().contains(to), from + " must depend on " + to + " in " + cycle);
        }
    }

    @Test
    void reportsRealClosedCycleAmongUnrelatedValidTasks() {
        String text = csv("D,1,0,", "A,1,0,B", "B,1,0,C", "C,1,0,A", "E,1,0,D");
        SchedulerException error = failure(text);
        assertEquals(List.of("A", "B", "C", "A"), error.cycle());
        assertValidCycle(error, List.of(
                new Task("A", 1, 0, List.of("B")), new Task("B", 1, 0, List.of("C")), new Task("C", 1, 0, List.of("A"))));
    }

    @Test
    void reportsTwoTaskCycle() {
        SchedulerException error = failure(csv("A,1,0,B", "B,1,0,A"));
        assertEquals(List.of("A", "B", "A"), error.cycle());
    }

    @Test
    void cycleReachedThroughAnAcyclicPrefixReportsOnlyTheCycleNotThePrefix() {
        // X -> A -> B -> C -> B : the cycle is B,C,B and must not include X or A.
        SchedulerException error = failure(csv("X,1,0,A", "A,1,0,B", "B,1,0,C", "C,1,0,B"));
        assertEquals(List.of("B", "C", "B"), error.cycle());
    }

    @Test
    void diamondDependenciesAreNotMistakenForCycles() throws SchedulerException {
        assertEquals(4, TaskLoader.parse(csv("A,1,0,", "B,1,0,A", "C,1,0,A", "D,1,0,\"B,C\"")).size());
    }

    @Test
    void longChainDoesNotOverflowTheStackOrReportACycle() throws SchedulerException {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            lines.add("T" + i + ",1,0," + (i == 0 ? "" : "T" + (i - 1)));
        }
        assertEquals(2000, TaskLoader.parse(csv(lines.toArray(String[]::new))).size());
        Collections.reverse(lines); // forward references everywhere
        assertEquals(2000, TaskLoader.parse(csv(lines.toArray(String[]::new))).size());
    }

    @Test
    void longCycleOf2000TasksIsReported() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            lines.add("T" + i + ",1,0,T" + ((i + 1) % 2000));
        }
        SchedulerException error = failure(csv(lines.toArray(String[]::new)));
        assertEquals(ErrorCode.CYCLE_DETECTED, error.code());
        assertEquals(2001, error.cycle().size());
    }

    // ---- file access ----

    @Test
    void missingFileAndDirectoryAreReadErrors(@TempDir Path directory) {
        assertEquals(ErrorCode.READ_ERROR,
                assertThrows(SchedulerException.class, () -> TaskLoader.loadFile(directory.resolve("nope.csv"))).code());
        assertEquals(ErrorCode.READ_ERROR,
                assertThrows(SchedulerException.class, () -> TaskLoader.loadFile(directory)).code());
    }

    @Test
    void loadsUtf8FileFromDisk(@TempDir Path directory) throws IOException, SchedulerException {
        Path file = directory.resolve("tasks.csv");
        Files.write(file, csv("A,1,0,").getBytes(StandardCharsets.UTF_8));
        assertEquals(1, TaskLoader.loadFile(file).size());
    }
}
