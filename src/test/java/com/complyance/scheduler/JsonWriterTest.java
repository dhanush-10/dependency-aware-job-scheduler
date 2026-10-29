package com.complyance.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonWriterTest {

    @Test
    void errorDocumentRoundTripsHostileMessageText() {
        String hostile = "quote\" back\\slash tab\t newline\n caf\u00E9 \u0001 emoji \uD83D\uDE00";
        String json = JsonWriter.error(new SchedulerException(ErrorCode.READ_ERROR, hostile));
        assertTrue(json.chars().allMatch(c -> c < 0x80), "output must be pure ASCII");
        Map<String, Object> parsed = MiniJson.parseObject(json);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = ((List<Map<String, Object>>) parsed.get("errors")).get(0);
        assertEquals(hostile, error.get("message"));
        assertEquals("READ_ERROR", error.get("code"));
    }

    @Test
    void cycleIsOnlyPresentForCycleErrors() {
        String withCycle = JsonWriter.error(new SchedulerException(ErrorCode.CYCLE_DETECTED, "cycle", List.of("A", "B", "A")));
        String without = JsonWriter.error(new SchedulerException(ErrorCode.INVALID_TASK, "bad"));
        assertTrue(withCycle.contains("\"cycle\": [\"A\", \"B\", \"A\"]"));
        assertFalse(without.contains("cycle"));
    }

    @Test
    void successDocumentContainsRequiredFieldsAndNoErrors() {
        ScheduleResult result = new ScheduleResult(2, 5, List.of(new ScheduledTask("A", 1, 0, 5)));
        Map<String, Object> parsed = MiniJson.parseObject(JsonWriter.success(result));
        assertEquals("ok", parsed.get("status"));
        assertEquals(2L, parsed.get("workers"));
        assertEquals(5L, parsed.get("makespan"));
        assertEquals(List.of(Map.of("task_id", "A", "worker_id", 1L, "start", 0L, "end", 5L)), parsed.get("schedule"));
        assertFalse(parsed.containsKey("errors"));
    }

    @Test
    void emptyScheduleRendersAsEmptyArray() {
        Map<String, Object> parsed = MiniJson.parseObject(JsonWriter.success(new ScheduleResult(3, 0, List.of())));
        assertEquals(List.of(), parsed.get("schedule"));
        assertEquals(0L, parsed.get("makespan"));
    }
}
