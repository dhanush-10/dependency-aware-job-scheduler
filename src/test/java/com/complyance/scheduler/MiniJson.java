package com.complyance.scheduler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny JSON reader used only by the tests, so that CLI output can be checked structurally
 * (key order and whitespace must not matter) without adding a JSON library. Numbers are Long.
 */
final class MiniJson {
    private final String text;
    private int pos;

    private MiniJson(String text) {
        this.text = text;
    }

    /** Parses exactly one JSON document; anything after it is an error. */
    static Object parse(String text) {
        MiniJson reader = new MiniJson(text);
        Object value = reader.value();
        reader.skipSpace();
        if (reader.pos != text.length()) {
            throw new IllegalArgumentException("Trailing content after JSON document at " + reader.pos);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String text) {
        return (Map<String, Object>) parse(text);
    }

    private Object value() {
        skipSpace();
        char c = text.charAt(pos);
        if (c == '{') {
            return object();
        }
        if (c == '[') {
            return array();
        }
        if (c == '"') {
            return string();
        }
        if (text.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (text.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        if (text.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        int start = pos;
        while (pos < text.length() && "-0123456789".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw new IllegalArgumentException("Unexpected character at " + pos);
        }
        return Long.parseLong(text.substring(start, pos));
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipSpace();
        if (text.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipSpace();
            String key = string();
            skipSpace();
            expect(':');
            map.put(key, value());
            skipSpace();
            if (text.charAt(pos) == ',') {
                pos++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        pos++;
        skipSpace();
        if (text.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(value());
            skipSpace();
            if (text.charAt(pos) == ',') {
                pos++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (text.charAt(pos) != '"') {
            char c = text.charAt(pos++);
            if (c == '\\') {
                char escape = text.charAt(pos++);
                switch (escape) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> out.append(escape);
                }
            } else {
                out.append(c);
            }
        }
        pos++;
        return out.toString();
    }

    private void expect(char expected) {
        if (text.charAt(pos) != expected) {
            throw new IllegalArgumentException("Expected '" + expected + "' at " + pos);
        }
        pos++;
    }

    private void skipSpace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }
}
