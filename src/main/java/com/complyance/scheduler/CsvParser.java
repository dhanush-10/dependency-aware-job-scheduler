package com.complyance.scheduler;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 style CSV reader: quoted fields, escaped quotes (""), commas and line breaks
 * inside quotes, LF and CRLF record separators. Fields are returned exactly as written
 * (outside the quotes); trimming and validation belong to the caller.
 */
final class CsvParser {

    /** One CSV record and the 1-based line where it starts. */
    record CsvRecord(int line, List<String> fields) {
    }

    private CsvParser() {
    }

    static List<CsvRecord> parse(String text) throws SchedulerException {
        List<CsvRecord> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;       // currently inside a quoted field
        boolean afterQuote = false;     // closing quote seen; only whitespace or a delimiter may follow
        boolean fieldWasQuoted = false;
        int line = 1;
        int recordStartLine = 1;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                        afterQuote = true;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
                afterQuote = false;
                fieldWasQuoted = false;
            } else if (c == '\n' || (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n')) {
                if (c == '\r') {
                    i++;
                }
                fields.add(field.toString());
                records.add(new CsvRecord(recordStartLine, fields));
                fields = new ArrayList<>();
                field.setLength(0);
                afterQuote = false;
                fieldWasQuoted = false;
                line++;
                recordStartLine = line;
            } else if (c == '"') {
                if (afterQuote || fieldWasQuoted || !isBlank(field)) {
                    throw malformed(line, "unexpected quote character inside an unquoted field");
                }
                field.setLength(0); // drop whitespace before the opening quote
                inQuotes = true;
                fieldWasQuoted = true;
            } else if (afterQuote) {
                if (!isSpaceOrTab(c)) {
                    throw malformed(line, "unexpected character after closing quote");
                }
            } else if (c == '\r') {
                throw malformed(line, "bare carriage return (only LF or CRLF line endings are supported)");
            } else {
                field.append(c);
            }
        }

        if (inQuotes) {
            throw malformed(recordStartLine, "unterminated quoted field");
        }
        if (field.length() > 0 || !fields.isEmpty() || fieldWasQuoted) {
            fields.add(field.toString());
            records.add(new CsvRecord(recordStartLine, fields));
        }
        return records;
    }

    private static boolean isBlank(StringBuilder text) {
        for (int i = 0; i < text.length(); i++) {
            if (!isSpaceOrTab(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSpaceOrTab(char c) {
        return c == ' ' || c == '\t';
    }

    private static SchedulerException malformed(int line, String reason) {
        return new SchedulerException(ErrorCode.INVALID_CSV, "Malformed CSV at line " + line + ": " + reason);
    }
}
