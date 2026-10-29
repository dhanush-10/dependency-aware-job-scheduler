package com.complyance.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** End-to-end tests through {@link Main#run}: arguments, stdout JSON, stderr and exit codes. */
class CliTest {

    private record Outcome(int exitCode, String stdout, String stderr) {
        Map<String, Object> json() {
            return MiniJson.parseObject(stdout);
        }
    }

    private static Outcome run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Outcome(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static Path write(Path directory, String content) throws IOException {
        Path file = directory.resolve("tasks.csv");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    // ---- the seven published samples, compared against expected/*.json ----

    static Stream<Arguments> publishedSamples() {
        return Stream.of(
                Arguments.of("basic", 2), Arguments.of("simultaneous", 2), Arguments.of("idle-and-ties", 3),
                Arguments.of("empty", 2), Arguments.of("unknown-dependency", 2), Arguments.of("cycle", 2),
                Arguments.of("duplicate-id", 2));
    }

    @ParameterizedTest(name = "{0} with {1} workers")
    @MethodSource("publishedSamples")
    @SuppressWarnings("unchecked")
    void matchesPublishedExpectedOutput(String name, int workers) throws IOException {
        Outcome outcome = run("--input", "inputs/" + name + ".csv", "--workers", String.valueOf(workers));
        Map<String, Object> expected = MiniJson.parseObject(Files.readString(Path.of("expected/" + name + ".json")));
        Map<String, Object> actual = outcome.json();

        assertEquals(expected.get("status"), actual.get("status"));
        if (expected.get("status").equals("ok")) {
            assertEquals(0, outcome.exitCode());
            assertEquals(expected.get("workers"), actual.get("workers"));
            assertEquals(expected.get("makespan"), actual.get("makespan"));
            assertEquals(expected.get("schedule"), actual.get("schedule")); // list equality keeps ordering
        } else {
            assertNotEquals(0, outcome.exitCode());
            assertFalse(actual.containsKey("schedule"), "no partial schedule on error");
            Map<String, Object> expectedError = ((List<Map<String, Object>>) expected.get("errors")).get(0);
            Map<String, Object> actualError = ((List<Map<String, Object>>) actual.get("errors")).get(0);
            assertEquals(expectedError.get("code"), actualError.get("code"));
            assertFalse(((String) actualError.get("message")).isBlank());
            if (expectedError.containsKey("cycle")) {
                List<String> cycle = (List<String>) actualError.get("cycle");
                assertEquals(cycle.get(0), cycle.get(cycle.size() - 1));
                assertEquals(4, cycle.size()); // A -> B -> C -> A is the only cycle in cycle.csv
            }
        }
    }

    // ---- output contract ----

    @Test
    @SuppressWarnings("unchecked")
    void successWritesOneJsonDocumentToStdoutAndNothingToStderr() {
        Outcome outcome = run("--input", "inputs/basic.csv", "--workers", "2");
        assertEquals(0, outcome.exitCode());
        assertEquals("", outcome.stderr());
        assertEquals("ok", outcome.json().get("status")); // parse() rejects trailing content, so exactly one document
        for (Map<String, Object> entry : (List<Map<String, Object>>) outcome.json().get("schedule")) {
            assertEquals(Long.class, entry.get("start").getClass());
            assertEquals((Long) entry.get("end") - (Long) entry.get("start"),
                    Map.of("A", 3L, "B", 2L, "C", 4L, "D", 1L).get(entry.get("task_id")));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorHasNonemptyMessageNoScheduleAndStderrDiagnostic() {
        Outcome outcome = run("--input", "inputs/unknown-dependency.csv", "--workers", "2");
        assertEquals(1, outcome.exitCode());
        assertNull(outcome.json().get("schedule"));
        assertTrue(outcome.stderr().contains("INVALID_DEPENDENCY"));
        List<Map<String, Object>> errors = (List<Map<String, Object>>) outcome.json().get("errors");
        assertEquals(1, errors.size());
        assertFalse(errors.get(0).containsKey("cycle"));
    }

    private static String[] codeAndMessage(Outcome outcome) {
        @SuppressWarnings("unchecked")
        Map<String, Object> error = ((List<Map<String, Object>>) outcome.json().get("errors")).get(0);
        return new String[] {(String) error.get("code"), (String) error.get("message")};
    }

    // ---- arguments ----

    @Test
    void argumentOrderDoesNotMatter() {
        Outcome outcome = run("--workers", "2", "--input", "inputs/basic.csv");
        assertEquals(0, outcome.exitCode());
        assertEquals(6L, outcome.json().get("makespan"));
    }

    @ParameterizedTest
    @MethodSource("badArguments")
    void invalidArgumentsGiveInvalidArgumentError(List<String> arguments) {
        Outcome outcome = run(arguments.toArray(String[]::new));
        assertNotEquals(0, outcome.exitCode());
        assertEquals("INVALID_ARGUMENT", codeAndMessage(outcome)[0]);
        assertNull(outcome.json().get("schedule"));
    }

    static Stream<List<String>> badArguments() {
        String f = "inputs/basic.csv";
        return Stream.of(
                List.of(),
                List.of("--input", f),
                List.of("--workers", "2"),
                List.of("--input", f, "--workers"),
                List.of("--input", f, "--workers", "0"),
                List.of("--input", f, "--workers", "33"),
                List.of("--input", f, "--workers", "-1"),
                List.of("--input", f, "--workers", "+2"),
                List.of("--input", f, "--workers", "2.0"),
                List.of("--input", f, "--workers", "two"),
                List.of("--input", f, "--workers", ""),
                List.of("--input", f, "--workers", "99999999999999999999"),
                List.of("--input", f, "--workers", "2", "--workers", "3"),
                List.of("--input", f, "--input", f, "--workers", "2"),
                List.of("--input", f, "--workers", "2", "--verbose"));
    }

    @Test
    void workerBoundsOneAndThirtyTwoAreAccepted() {
        assertEquals(1L, run("--input", "inputs/basic.csv", "--workers", "1").json().get("workers"));
        assertEquals(32L, run("--input", "inputs/basic.csv", "--workers", "32").json().get("workers"));
    }

    @Test
    void missingInputFileIsReadError() {
        Outcome outcome = run("--input", "inputs/does-not-exist.csv", "--workers", "2");
        assertEquals(1, outcome.exitCode());
        assertEquals("READ_ERROR", codeAndMessage(outcome)[0]);
    }

    @Test
    void invalidArgumentWinsOverUnreadableFile() {
        Outcome outcome = run("--input", "inputs/does-not-exist.csv", "--workers", "0");
        assertEquals("INVALID_ARGUMENT", codeAndMessage(outcome)[0]);
    }

    // ---- file-level behaviour through the CLI ----

    @Test
    void invalidCsvTaskAndDependencyErrorsExitNonzeroWithMatchingCodes(@TempDir Path directory) throws IOException {
        String header = "task_id,duration,priority,dependencies\n";
        String[][] cases = {
                {"INVALID_CSV", "id,duration\nA,1\n"},
                {"INVALID_CSV", header + "A,1,0\n"},
                {"INVALID_TASK", header + "A,0,0,\n"},
                {"INVALID_TASK", header + "A,1,1001,\n"},
                {"DUPLICATE_TASK", header + "A,1,0,\nA,1,0,\n"},
                {"INVALID_DEPENDENCY", header + "A,1,0,A\n"},
                {"INVALID_DEPENDENCY", header + "A,1,0,\nB,1,0,\"A,A\"\n"},
                {"CYCLE_DETECTED", header + "A,1,0,B\nB,1,0,A\nC,1,0,\n"},
        };
        for (String[] testCase : cases) {
            Outcome outcome = run("--input", write(directory, testCase[1]).toString(), "--workers", "2");
            assertEquals(1, outcome.exitCode(), testCase[1]);
            assertEquals(testCase[0], codeAndMessage(outcome)[0], testCase[1]);
            assertNull(outcome.json().get("schedule"), "partial schedule leaked for: " + testCase[1]);
        }
    }

    @Test
    void repeatedRunsAreIndependentAndIdentical() {
        String first = run("--input", "inputs/simultaneous.csv", "--workers", "2").stdout();
        run("--input", "inputs/idle-and-ties.csv", "--workers", "3");
        assertEquals(first, run("--input", "inputs/simultaneous.csv", "--workers", "2").stdout());
    }

    @Test
    void outputIsSortedByStartThenWorker() {
        List<?> schedule = (List<?>) run("--input", "inputs/simultaneous.csv", "--workers", "2").json().get("schedule");
        long previousStart = -1;
        long previousWorker = -1;
        for (Object item : schedule) {
            Map<?, ?> entry = (Map<?, ?>) item;
            long start = (Long) entry.get("start");
            long worker = (Long) entry.get("worker_id");
            assertTrue(start > previousStart || (start == previousStart && worker > previousWorker));
            previousStart = start;
            previousWorker = worker;
        }
    }
}
