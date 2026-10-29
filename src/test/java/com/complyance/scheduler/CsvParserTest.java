package com.complyance.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvParserTest {

    private static List<List<String>> fields(String text) throws SchedulerException {
        return CsvParser.parse(text).stream().map(CsvParser.CsvRecord::fields).toList();
    }

    @Test
    void parsesPlainAndQuotedFieldsWithEmbeddedCommas() throws SchedulerException {
        assertEquals(List.of(List.of("D", "1", "2", "B,C")), fields("D,1,2,\"B,C\"\n"));
    }

    @Test
    void supportsEscapedQuotesAndLineBreaksInsideQuotes() throws SchedulerException {
        assertEquals(List.of(List.of("a\"b", "line1\nline2")), fields("\"a\"\"b\",\"line1\nline2\"\n"));
    }

    @Test
    void acceptsLfCrlfAndMissingFinalNewline() throws SchedulerException {
        assertEquals(List.of(List.of("a", "b"), List.of("c", "d")), fields("a,b\r\nc,d"));
        assertEquals(List.of(List.of("a", "b"), List.of("c", "d")), fields("a,b\nc,d\n"));
    }

    @Test
    void trailingEmptyFieldIsKept() throws SchedulerException {
        assertEquals(List.of(List.of("A", "3", "2", "")), fields("A,3,2,\n"));
    }

    @Test
    void recordLineNumbersCountLinesInsideQuotedFields() throws SchedulerException {
        List<CsvParser.CsvRecord> records = CsvParser.parse("a\n\"x\ny\"\nb\n");
        assertEquals(List.of(1, 2, 4), records.stream().map(CsvParser.CsvRecord::line).toList());
    }

    @Test
    void rejectsUnterminatedQuote() {
        assertEquals(ErrorCode.INVALID_CSV, assertThrows(SchedulerException.class, () -> CsvParser.parse("a,\"b\n")).code());
    }

    @Test
    void rejectsQuoteInsideUnquotedField() {
        assertEquals(ErrorCode.INVALID_CSV, assertThrows(SchedulerException.class, () -> CsvParser.parse("a,b\"c\n")).code());
    }

    @Test
    void rejectsTextAfterClosingQuote() {
        assertEquals(ErrorCode.INVALID_CSV, assertThrows(SchedulerException.class, () -> CsvParser.parse("\"a\"x,b\n")).code());
    }

    @Test
    void rejectsBareCarriageReturn() {
        assertEquals(ErrorCode.INVALID_CSV, assertThrows(SchedulerException.class, () -> CsvParser.parse("a,b\rc,d\n")).code());
    }
}
