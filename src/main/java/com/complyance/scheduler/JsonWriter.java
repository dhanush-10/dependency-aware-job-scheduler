package com.complyance.scheduler;

import java.util.List;

/** Renders the two output documents. Hand-written because the schema is tiny and fixed. */
final class JsonWriter {
    private JsonWriter() {
    }

    static String success(ScheduleResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"status\": \"ok\",\n");
        json.append("  \"workers\": ").append(result.workers()).append(",\n");
        json.append("  \"makespan\": ").append(result.makespan()).append(",\n");
        if (result.schedule().isEmpty()) {
            json.append("  \"schedule\": []\n");
        } else {
            json.append("  \"schedule\": [\n");
            List<ScheduledTask> entries = result.schedule();
            for (int i = 0; i < entries.size(); i++) {
                ScheduledTask entry = entries.get(i);
                json.append("    { \"task_id\": ").append(quote(entry.taskId()))
                        .append(", \"worker_id\": ").append(entry.workerId())
                        .append(", \"start\": ").append(entry.start())
                        .append(", \"end\": ").append(entry.end()).append(" }")
                        .append(i < entries.size() - 1 ? ",\n" : "\n");
            }
            json.append("  ]\n");
        }
        return json.append("}\n").toString();
    }

    static String error(SchedulerException error) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"status\": \"error\",\n  \"errors\": [\n    {\n");
        json.append("      \"code\": ").append(quote(error.code().name())).append(",\n");
        json.append("      \"message\": ").append(quote(error.getMessage()));
        if (error.cycle() != null) {
            json.append(",\n      \"cycle\": [");
            for (int i = 0; i < error.cycle().size(); i++) {
                json.append(i == 0 ? "" : ", ").append(quote(error.cycle().get(i)));
            }
            json.append("]");
        }
        return json.append("\n    }\n  ]\n}\n").toString();
    }

    /** JSON string literal. Everything outside printable ASCII is escaped, so output is encoding-proof. */
    static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c > 0x7E) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
