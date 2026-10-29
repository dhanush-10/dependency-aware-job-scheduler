package com.complyance.scheduler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads the CSV input contract and returns validated tasks, or throws a {@link SchedulerException}. */
public final class TaskLoader {
    static final int MAX_TASKS = 2000;
    static final int MAX_DURATION = 1_000_000;
    static final int MAX_PRIORITY = 1_000;
    private static final List<String> EXPECTED_HEADER = List.of("task_id", "duration", "priority", "dependencies");
    private static final Pattern TASK_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,31}");
    private static final Pattern UNSIGNED_DECIMAL = Pattern.compile("[0-9]+");

    private TaskLoader() {
    }

    public static List<Task> loadFile(Path path) throws SchedulerException {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException | RuntimeException e) {
            throw new SchedulerException(ErrorCode.READ_ERROR,
                    "Cannot read input file '" + path + "': " + e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")"));
        }
        return parse(bytes);
    }

    public static List<Task> parse(byte[] bytes) throws SchedulerException {
        return parse(decodeUtf8(bytes));
    }

    public static List<Task> parse(String text) throws SchedulerException {
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        List<CsvParser.CsvRecord> records = new ArrayList<>();
        for (CsvParser.CsvRecord record : CsvParser.parse(text)) {
            if (!isIgnorableBlank(record)) {
                records.add(record);
            }
        }
        if (records.isEmpty()) {
            throw new SchedulerException(ErrorCode.INVALID_CSV,
                    "Input has no header row; expected: " + String.join(",", EXPECTED_HEADER));
        }
        checkHeader(records.get(0));

        int taskCount = records.size() - 1;
        if (taskCount > MAX_TASKS) {
            throw new SchedulerException(ErrorCode.INVALID_TASK,
                    "Input has " + taskCount + " tasks; the maximum is " + MAX_TASKS);
        }

        List<Task> tasks = new ArrayList<>(taskCount);
        Map<String, Task> byId = new HashMap<>();
        for (CsvParser.CsvRecord record : records.subList(1, records.size())) {
            Task task = toTask(record);
            if (byId.putIfAbsent(task.id(), task) != null) {
                throw new SchedulerException(ErrorCode.DUPLICATE_TASK,
                        "Task ID " + task.id() + " occurs more than once (line " + record.line() + ")");
            }
            tasks.add(task);
        }
        validateDependencies(tasks, byId);
        CycleDetector.requireAcyclic(tasks);
        return tasks;
    }

    private static String decodeUtf8(byte[] bytes) throws SchedulerException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new SchedulerException(ErrorCode.INVALID_CSV, "Input is not valid UTF-8");
        }
    }

    /** Blank lines and single-field whitespace-only records are skipped; ",,," is not. */
    private static boolean isIgnorableBlank(CsvParser.CsvRecord record) {
        return record.fields().size() == 1 && trim(record.fields().get(0)).isEmpty();
    }

    private static void checkHeader(CsvParser.CsvRecord header) throws SchedulerException {
        List<String> names = header.fields().stream().map(TaskLoader::trim).toList();
        if (!names.equals(EXPECTED_HEADER)) {
            throw new SchedulerException(ErrorCode.INVALID_CSV,
                    "Header must be exactly " + String.join(",", EXPECTED_HEADER) + " but was "
                            + String.join(",", names).replaceAll("[^\\x20-\\x7E]", "?") + " (line " + header.line() + ")");
        }
    }

    private static Task toTask(CsvParser.CsvRecord record) throws SchedulerException {
        List<String> fields = record.fields();
        int line = record.line();
        if (fields.size() != EXPECTED_HEADER.size()) {
            throw new SchedulerException(ErrorCode.INVALID_CSV,
                    "Line " + line + " has " + fields.size() + " columns; expected " + EXPECTED_HEADER.size());
        }
        String id = trim(fields.get(0));
        if (!TASK_ID.matcher(id).matches()) {
            throw new SchedulerException(ErrorCode.INVALID_TASK,
                    "Line " + line + ": invalid task_id (must match [A-Za-z][A-Za-z0-9_-]{0,31})");
        }
        int duration = parseBounded(id, "duration", trim(fields.get(1)), 1, MAX_DURATION, line);
        int priority = parseBounded(id, "priority", trim(fields.get(2)), 0, MAX_PRIORITY, line);
        return new Task(id, duration, priority, splitDependencies(trim(fields.get(3))));
    }

    private static int parseBounded(String taskId, String column, String value, int min, int max, int line)
            throws SchedulerException {
        String problem = "Task " + taskId + " (line " + line + "): " + column + " must be an unsigned integer from "
                + min + " to " + max;
        if (!UNSIGNED_DECIMAL.matcher(value).matches()) {
            throw new SchedulerException(ErrorCode.INVALID_TASK, problem);
        }
        String digits = value.replaceFirst("^0+(?=.)", ""); // leading zeroes are allowed
        if (digits.length() > 9) {                            // too large for int; certainly out of range
            throw new SchedulerException(ErrorCode.INVALID_TASK, problem);
        }
        int number = Integer.parseInt(digits);
        if (number < min || number > max) {
            throw new SchedulerException(ErrorCode.INVALID_TASK, problem);
        }
        return number;
    }

    /** Splits a dependency list. Token validity is checked later, once all task IDs are known. */
    private static List<String> splitDependencies(String field) {
        if (field.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : field.split(",", -1)) { // -1 keeps trailing empty tokens so they are rejected
            tokens.add(trim(token));
        }
        return tokens;
    }

    private static void validateDependencies(List<Task> tasks, Map<String, Task> byId) throws SchedulerException {
        for (Task task : tasks) {
            Set<String> seen = new HashSet<>();
            for (String dependency : task.dependencies()) {
                if (dependency.isEmpty()) {
                    throw invalidDependency("Task " + task.id() + " has an empty entry in its dependency list");
                }
                if (!TASK_ID.matcher(dependency).matches()) {
                    throw invalidDependency("Task " + task.id() + " has a malformed dependency ID");
                }
                if (dependency.equals(task.id())) {
                    throw invalidDependency("Task " + task.id() + " depends on itself");
                }
                if (!seen.add(dependency)) {
                    throw invalidDependency("Task " + task.id() + " lists dependency " + dependency + " more than once");
                }
                if (!byId.containsKey(dependency)) {
                    throw invalidDependency("Task " + task.id() + " depends on unknown task " + dependency);
                }
            }
        }
    }

    private static SchedulerException invalidDependency(String message) {
        return new SchedulerException(ErrorCode.INVALID_DEPENDENCY, message);
    }

    /** Trims outer ASCII whitespace only (space, tab, CR, LF, FF, VT), unlike String.strip(). */
    static String trim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isAsciiWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && isAsciiWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isAsciiWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == 0x0B;
    }
}
